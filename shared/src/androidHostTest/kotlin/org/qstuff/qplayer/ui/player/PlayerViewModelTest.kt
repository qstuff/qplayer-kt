package org.qstuff.qplayer.ui.player

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.ui.player.mediaservice.TransportCommand
import org.qstuff.qplayer.util.JogwheelMode
import org.qstuff.qplayer.util.PlayerStatus
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    /** Records the commands; state flows can be driven by the test. */
    private class FakeAudioPlayer : AudioPlayer {
        override val isConnected = MutableStateFlow(false)
        override val trackStatus = MutableSharedFlow<Track>(replay = 1, extraBufferCapacity = 8)
        override val playing = MutableStateFlow(false)
        override val transportCommands = MutableSharedFlow<TransportCommand>(extraBufferCapacity = 4)
        val calls = mutableListOf<String>()
        var position = 0L

        override fun connect() { isConnected.value = true }
        override fun disconnect() { isConnected.value = false }
        override fun loadTrack(track: Track) { calls += "load ${track.uri}" }
        override fun play() { calls += "play"; playing.value = true }
        override fun pause() { calls += "pause"; playing.value = false }
        override fun seekTo(positionMs: Long, andPause: Boolean) {
            calls += "seek $positionMs" + if (andPause) " pause" else ""
            position = positionMs
        }
        override fun setSpeed(factor: Float, masterTempo: Boolean) { calls += "speed $factor $masterTempo" }
        override val positionMs get() = position
        override val isPlaying get() = playing.value
        override fun releaseForegroundHold() { calls += "releaseForegroundHold" }

        /** The engine reports a status transition of [track]. */
        fun report(track: Track, status: Track.TrackStatus) {
            track.trackStatus = status
            trackStatus.tryEmit(track)
        }
    }

    private class FakeScratcher : Scratcher {
        override var isScratching = false
        override var positionMs = 0L
        var startPossible = true
        override fun prepare(scope: CoroutineScope, uri: String) {}
        override fun release() {}
        override fun start(positionMs: Long, sensitivity: Int): Boolean {
            if (!startPossible) return false
            isScratching = true
            this.positionMs = positionMs
            return true
        }
        override fun scratchTo(rotationRad: Double) { positionMs += (rotationRad * 1000).toLong() }
        override fun stop(): Long { isScratching = false; return positionMs }
    }

    private val main = UnconfinedTestDispatcher()
    private val player = FakeAudioPlayer()
    private val scratcher = FakeScratcher()
    private val prefs = PreferencesDataSource(MapSettings())
    private val originalLocale = Locale.getDefault()

    private fun viewModel() = PlayerViewModel(player, scratcher, { null }, prefs)

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `a track loaded before the engine is connected is loaded once it is`() {
        val vm = viewModel()
        vm.loadTrack(Track.fromPath("/music/a.mp3"))
        assertEquals(emptyList<String>(), player.calls)

        player.connect()

        assertTrue("load /music/a.mp3" in player.calls)
        assertEquals(Track.TrackStatus.LOADING, vm.playerTrackState.value?.status)
    }

    @Test
    fun `a prepared track goes to its position and auto-plays`() {
        val vm = viewModel()
        player.connect()
        val track = Track.fromPath("/music/a.mp3", autoplay = true).apply { playPosition = 5000 }
        vm.loadTrack(track)
        player.calls.clear()

        player.report(track, Track.TrackStatus.PREPARED)

        assertEquals(listOf("seek 5000", "play"), player.calls)
        assertEquals(PlayerStatus.PLAYING, vm.playerStatus.value)
    }

    @Test
    fun `a prepared track without auto-play lets the engine leave the foreground`() {
        val vm = viewModel()
        player.connect()
        val track = Track.fromPath("/music/a.mp3")
        vm.loadTrack(track)
        player.calls.clear()

        player.report(track, Track.TrackStatus.PREPARED)

        assertEquals(listOf("seek 0", "releaseForegroundHold"), player.calls)
    }

    @Test
    fun `a finished track pauses and is announced`() = runTest(main) {
        val vm = viewModel()
        player.connect()
        val completed = mutableListOf<Track>()
        backgroundScope.launch { vm.trackCompleted.collect { completed += it } }
        val track = Track.fromPath("/music/a.mp3").apply { playPosition = 1234 }
        vm.loadTrack(track)
        player.calls.clear()

        player.report(track, Track.TrackStatus.COMPLETED)

        assertEquals(listOf("pause"), player.calls)
        assertEquals(listOf(track), completed)
        assertEquals(0L, track.playPosition)
    }

    @Test
    fun `system media controls are forwarded`() = runTest(main) {
        val vm = viewModel()
        val commands = mutableListOf<TransportCommand>()
        backgroundScope.launch { vm.transportCommands.collect { commands += it } }

        player.transportCommands.tryEmit(TransportCommand.NEXT)

        assertEquals(listOf(TransportCommand.NEXT), commands)
    }

    @Test
    fun `prev restarts the track only after 3 seconds`() {
        val vm = viewModel()
        player.connect()

        player.position = 2_000
        assertFalse(vm.restartCurrentTrackIfPlayed())

        player.position = 4_000
        assertTrue(vm.restartCurrentTrackIfPlayed())
        assertEquals("seek 0", player.calls.last())
    }

    @Test
    fun `pitch sets text and speed, and is saved shortly after`() = runTest(main) {
        val vm = viewModel()
        player.connect()

        vm.onPitchChanged(625)          // pitch range ±8 %: (625 - 500) / 62.5 = +2.0 %

        assertEquals("  +2,0%", vm.pitchValueText.value)
        assertEquals("speed 1.02 false", player.calls.last())
        assertEquals(500, prefs.readPitchValue())
        advanceTimeBy(301)
        assertEquals(625, prefs.readPitchValue())
    }

    @Test
    fun `the fader value follows every pitch change and the range`() {
        val vm = viewModel()
        player.connect()

        vm.onPitchChanged(625)
        assertEquals(625, vm.pitchValue.value)

        vm.onPitchChanged(500)
        assertEquals(500, vm.pitchValue.value)
        assertEquals("speed 1.0 false", player.calls.last())

        vm.onPitchChanged(625)          // ±8 % (62.5 per %): +2 %
        vm.onPitchRangeSelected(2)      // ±50 % (10 per %): same +2 % = 520
        assertEquals(520, vm.pitchValue.value)
        assertEquals("  +2,0%", vm.pitchValueText.value)
    }

    @Test
    fun `scratching pauses the player and hands the end position back`() {
        prefs.setJogWheelMode(2)        // SCRATCH
        val vm = viewModel()
        player.connect()
        player.play()
        player.position = 10_000
        player.calls.clear()

        assertTrue(vm.startScratch())
        assertEquals(listOf("pause"), player.calls)
        assertEquals(PlayerStatus.PLAYING, vm.playerStatus.value)   // the deck keeps its state

        vm.scratchTo(2.0)
        vm.endScratch()

        assertEquals(listOf("pause", "seek 12000", "play"), player.calls)
    }

    @Test
    fun `the jog mode quick switch writes the setting, which is applied like from Settings`() {
        val vm = viewModel()
        assertEquals(JogwheelMode.SPEED_ANGULAR, vm.jogwheelMode.value)

        vm.setJogwheelMode(JogwheelMode.SCRATCH)
        assertEquals(JogwheelMode.SCRATCH, prefs.getJogWheelModeEnum())

        vm.loadSettings()               // what PlaybackCoordinator does on settingsChanged
        assertEquals(JogwheelMode.SCRATCH, vm.jogwheelMode.value)
        player.connect()
        assertTrue(vm.startScratch())   // scratch mode is active
    }

    @Test
    fun `the sensitivity quick switch writes the setting, which is applied like from Settings`() {
        val vm = viewModel()

        vm.setJogwheelSensitivity(20)
        assertEquals(20, prefs.getJogWheelSensitivity())

        vm.loadSettings()               // what PlaybackCoordinator does on settingsChanged
        assertEquals(20, vm.jogwheelSensitivity.value)
    }

    @Test
    fun `no scratching outside scratch mode`() {
        val vm = viewModel()
        player.connect()

        assertFalse(vm.startScratch())
        assertEquals(emptyList<String>(), player.calls)
    }
}
