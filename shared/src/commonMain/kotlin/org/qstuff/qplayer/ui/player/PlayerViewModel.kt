package org.qstuff.qplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.ui.player.mediaservice.TransportCommand
import org.qstuff.qplayer.util.Formatting
import org.qstuff.qplayer.util.JogwheelMode
import org.qstuff.qplayer.util.LruCache
import org.qstuff.qplayer.util.PlayerStatus
import co.touchlab.kermit.Logger

private val log = Logger.withTag("PlayerViewModel")

/**
 * The deck: track loading and status, play/pause, pitch, cue, waveform and scratching. Platform
 * independent: talks to the platform only through [AudioPlayer], [Scratcher] and
 * [WaveformGenerator].
 */
class PlayerViewModel(
    private val audioPlayer: AudioPlayer,
    private val scratchEngine: Scratcher,
    private val waveformGenerator: WaveformGenerator,
    private val preferencesDataSource: PreferencesDataSource
) : ViewModel() {

    companion object {
        val PITCH_RANGE_FACTORS = floatArrayOf(62.5f, 33.3f, 10f, 5f)

        /** Prev restarts the current track if it has played longer than this, else goes back. */
        const val PREV_RESTART_THRESHOLD_MS = 3000L

        private const val PITCH_SAVE_DELAY_MS = 300L
    }

    // Overview waveform for the current track (generated in-VM; null while decoding / on change).
    private val _onWaveformDataUpdate = MutableStateFlow<TrackData?>(null)
    val onWaveformDataUpdate: StateFlow<TrackData?> = _onWaveformDataUpdate.asStateFlow()

    // Current track + status as an immutable snapshot; see PlayerTrackState. The revision makes
    // each emission distinct so re-selecting the same track still propagates through the equality
    // dedup of StateFlow / Compose State — no more version-counter observer in the UI.
    private val _playerTrackState = MutableStateFlow<PlayerTrackState?>(null)
    val playerTrackState: StateFlow<PlayerTrackState?> = _playerTrackState.asStateFlow()

    // One-shot event: the current track finished playing (playback is already stopped). The
    // PlaybackCoordinator forwards it to the queue to decide what follows. An event, not state — it must
    // fire once per completion, also while the app is in the background.
    private val _trackCompleted = MutableSharedFlow<Track>(extraBufferCapacity = 1)
    val trackCompleted: SharedFlow<Track> = _trackCompleted.asSharedFlow()
    private var trackStateRevision = 0

    // One-shot events: Next/Prev pressed in system media controls (notification, lock screen,
    // headset, Bluetooth). The PlaybackCoordinator routes them to the queue, like the on-screen buttons.
    private val _transportCommands =
        MutableSharedFlow<TransportCommand>(extraBufferCapacity = 4)
    val transportCommands: SharedFlow<TransportCommand> =
        _transportCommands.asSharedFlow()

    // Observables — plain UI state, exposed as read-only StateFlow.
    private val _playerStatus = MutableStateFlow(PlayerStatus.PAUSED)
    val playerStatus: StateFlow<PlayerStatus> = _playerStatus.asStateFlow()

    private val _onTrackPositionUpdate = MutableStateFlow(0L)
    val onTrackPositionUpdate: StateFlow<Long> = _onTrackPositionUpdate.asStateFlow()

    private val _pitchValueText = MutableStateFlow("0,0%")
    val pitchValueText: StateFlow<String> = _pitchValueText.asStateFlow()

    private val _pitchValue = MutableStateFlow(500)
    val pitchValue: StateFlow<Int> = _pitchValue.asStateFlow()

    private val _jogwheelSensitivity = MutableStateFlow(10)
    val jogwheelSensitivity: StateFlow<Int> = _jogwheelSensitivity.asStateFlow()

    private val _jogwheelMode = MutableStateFlow(JogwheelMode.SPEED_ANGULAR)
    val jogwheelMode: StateFlow<JogwheelMode> = _jogwheelMode.asStateFlow()

    // JogwheelHaptics.LEVEL_* (jog wheel vibration setting)
    private val _jogwheelHapticLevel = MutableStateFlow(2)
    val jogwheelHapticLevel: StateFlow<Int> = _jogwheelHapticLevel.asStateFlow()

    private val _pitchFactorIndex = MutableStateFlow(0)
    val pitchFactorIndex: StateFlow<Int> = _pitchFactorIndex.asStateFlow()

    private val _masterTempo = MutableStateFlow(false)
    val masterTempo: StateFlow<Boolean> = _masterTempo.asStateFlow()

    private val _cueActive = MutableStateFlow(false)
    val cueActive: StateFlow<Boolean> = _cueActive.asStateFlow()

    private val _showRemainingTime = MutableStateFlow(true)
    val showRemainingTime: StateFlow<Boolean> = _showRemainingTime.asStateFlow()

    // States
    var pitchFactor = PITCH_RANGE_FACTORS[0]
    private var currentPitchProgress = 0
    // Pitch is saved shortly after it stops changing (not on every fader frame) — not only in
    // saveState(): a killed process (new install from the IDE, low memory, crash) gets no
    // lifecycle callback, and the pitch was then restored from an old value.
    private var pitchSaveJob: Job? = null
    private var currentTrackSpeed = 0.0f
    private var showRemainingTrackTime = true

    // Settings
    private var autoStart = false
    private var isStopPlaybackOnSettingCuepointEnabled = false

    // Playback engine. A track loaded before the engine is connected is loaded once it is.
    private val isConnected get() = audioPlayer.isConnected.value
    private var pendingTrack: Track? = null

    // Position timer (runs while playing / scratching)
    private var updateJob: Job? = null

    // Waveform overview generation (native MediaExtractor+MediaCodec, off the main thread)
    private val waveformCache = LruCache<String, ByteArray>(32)
    private var waveformJob: Job? = null

    // Jog wheel SCRATCH mode: takes over the audio from the player while the wheel is touched.
    private var isScratchModeEnabled = false
    private var wasPlayingBeforeScratch = false

    init {
        loadStates()
        loadSettings()

        viewModelScope.launch {
            audioPlayer.trackStatus.collect { track -> emitTrackState(track, track.trackStatus) }
        }
        // The player's real play state is the source of truth for PLAYING/PAUSED and the position
        // timer — play/pause can also come from system media controls.
        viewModelScope.launch {
            audioPlayer.playing.collect { playing ->
                // While scratching the player is paused on purpose; the deck keeps its state.
                if (scratchEngine.isScratching) return@collect
                _playerStatus.value = if (playing) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                if (playing) startUpdateTimer() else resetUpdateTimer()
            }
        }
        viewModelScope.launch {
            audioPlayer.transportCommands.collect { _transportCommands.tryEmit(it) }
        }
        viewModelScope.launch {
            audioPlayer.isConnected.collect { connected ->
                if (connected) pendingTrack?.let { track -> pendingTrack = null; loadTrack(track) }
            }
        }
    }

    //
    // Player User Interaction
    //

    // Play/pause only command the player; playerStatus and the position timer follow the player's
    // real play state (see the `playing` collector in init).

    fun playPause() {
        if (!isConnected) return

        if (_playerStatus.value == PlayerStatus.PLAYING) audioPlayer.pause() else audioPlayer.play()
    }

    fun playFromCue(track: Track?) {

        if (!isConnected) return
        if (track == null) return

        audioPlayer.seekTo(track.cuePosition, true)
        audioPlayer.play()
    }

    fun toggleMasterTempo() {
        _masterTempo.value = !_masterTempo.value
        preferencesDataSource.saveMasterTempoMode(_masterTempo.value)
    }

    fun onPitchRangeSelected(index: Int) {

        pitchFactor = PITCH_RANGE_FACTORS[index]
        _pitchFactorIndex.value = index
        preferencesDataSource.savePitchFactorIndex(index)

        val progress = ((currentTrackSpeed -1) * 100 * pitchFactor + 500)
        onPitchChanged(progress.toInt())
    }

    fun onPitchChanged(progress: Int) {
        currentPitchProgress = progress
        // The fader shows this: one source of truth for fader, text and speed (a separate UI copy
        // went stale and showed an old value after the screen state was rebuilt).
        _pitchValue.value = progress
        pitchSaveJob?.cancel()
        pitchSaveJob = viewModelScope.launch {
            delay(PITCH_SAVE_DELAY_MS)
            preferencesDataSource.savePitchValue(progress)
        }

        val diff = (progress - 500).toFloat() / pitchFactor
        var pre = if (diff > 0) "+" else ""

        if (diff == 0f) {
            pre = "   "
        }
        if (diff in 0.0..10.0) {
            pre = "  +"
        }
        if (diff in -10.0..0.0) {
            pre = "  "
        }

        val pitch = pre + Formatting.oneDecimal(diff)

        if (diff in -99.0..99.0) {
            _pitchValueText.value = "$pitch%"
        } else {
            _pitchValueText.value = pitch
        }

        if (1.0f + diff / 100 < 0) {
            return
        }

        currentTrackSpeed = 1.0f + diff / 100

        if (isConnected) {
            audioPlayer.setSpeed(currentTrackSpeed, _masterTempo.value)
        }
    }

    fun toggleCue(track: Track, enable: Boolean) {
        _cueActive.value = enable

        if (isConnected) {
            if (enable) {
                if (isStopPlaybackOnSettingCuepointEnabled && audioPlayer.isPlaying) {
                    playPause()
                }
                track.cuePosition = audioPlayer.positionMs
            } else {
                track.cuePosition = 0
            }
        }
    }

    fun toggleDynamicTrackLengthDisplay() {
        showRemainingTrackTime = !showRemainingTrackTime
        _showRemainingTime.value = showRemainingTrackTime
        // The displayed time recomputes in the UI off showRemainingTime, so no position re-post
        // is needed here.
        preferencesDataSource.saveRemainingTimeMode(showRemainingTrackTime)
    }

    //
    // Playback engine
    //

    fun connectPlayer() = audioPlayer.connect()

    fun disconnectPlayer() = audioPlayer.disconnect()

    //
    // Track handling
    //

    fun loadTrack(track: Track?) {
        log.d { "loadTrack(): $track" }

        if (!isConnected) {
            pendingTrack = track
            return
        }

        if (track == null) {
            return
        }

        endScratch()

        val currentTrack = _playerTrackState.value?.track

        audioPlayer.pause()

        if (currentTrack?.uri == track.uri) {
            log.d { "loadTrack(): same track: $track" }

            currentTrack.playPosition = 0
            currentTrack.isAutoplay = autoStart
            emitTrackState(currentTrack, Track.TrackStatus.PREPARED)

        } else {
            log.d { "loadTrack(): new track: $track" }
            audioPlayer.loadTrack(track)
            emitTrackState(track, Track.TrackStatus.LOADING)
        }

        generateWaveform(track)
        prepareScratch()
    }

    /**
     * Publish an immutable [PlayerTrackState] snapshot (distinct on every call via revision) and
     * apply the playback side effects of the transition here in the ViewModel — not in the UI —
     * so auto-play and auto-advance also work while the app is in the background (the UI only
     * collects state while it's started).
     */
    private fun emitTrackState(track: Track, status: Track.TrackStatus) {
        track.trackStatus = status
        _playerTrackState.value = PlayerTrackState(track, status, ++trackStateRevision)

        when (status) {
            Track.TrackStatus.PREPARED -> onTrackPrepared(track)
            Track.TrackStatus.COMPLETED -> onTrackCompleted(track)
            else -> {}
        }
    }

    /**
     * A track is ready: go to its stored position and start it if it should auto-play. Explicit
     * play() (not the playPause() toggle), since the reported play state may lag a load.
     */
    private fun onTrackPrepared(track: Track) {
        if (!isConnected) return
        seekTo(track.playPosition.toDouble(), false)
        if (track.isAutoplay) {
            audioPlayer.play()
        } else {
            // Not continuing (e.g. auto-advanced with autostart off): drop the foreground hold
            // the service took when the previous track ended.
            audioPlayer.releaseForegroundHold()
        }
    }

    /**
     * The current track finished: stop playback (the play-state flow then stops the timer) and
     * announce it via [trackCompleted]. Whether (and how) a following track is loaded is the
     * queue's decision — a loaded track that should auto-play starts via [onTrackPrepared].
     */
    private fun onTrackCompleted(track: Track) {
        if (isConnected) audioPlayer.pause()
        track.playPosition = 0
        _trackCompleted.tryEmit(track)
    }

    /**
     * Nothing follows a finished track: rewind it (so Play starts it again) and let the service
     * leave the foreground.
     */
    fun stopAfterCompletion() {
        if (!isConnected) return
        seekTo(0.0, true)
        audioPlayer.releaseForegroundHold()
    }

    /**
     * Generate (or serve from cache) the overview waveform for [track] and publish it via
     * [onWaveformDataUpdate]. Decoding runs on a background dispatcher; the previous job is
     * cancelled when a new track is loaded, and stale results for an already-changed track are
     * discarded. viewModelScope cancels any in-flight job when the ViewModel is cleared.
     */
    private fun generateWaveform(track: Track) {
        val uri = track.uri

        waveformCache.get(uri)?.let { cached ->
            _onWaveformDataUpdate.value = TrackData(track, cached)
            return
        }

        // Clear any previous waveform while the new one is decoding.
        _onWaveformDataUpdate.value = null
        waveformJob?.cancel()
        waveformJob = viewModelScope.launch {
            val bytes = waveformGenerator.generate(uri)
            if (bytes != null && isActive) {
                waveformCache.put(uri, bytes)
                // Only publish if this is still the current track.
                if (_playerTrackState.value?.track?.uri == uri) {
                    _onWaveformDataUpdate.value = TrackData(track, bytes)
                }
            }
        }
    }

    //
    // Scratching (jog wheel SCRATCH mode)
    //

    /** Decode the current track for scratching if SCRATCH mode is on, else free the engine. */
    private fun prepareScratch() {
        val track = _playerTrackState.value?.track
        if (!isScratchModeEnabled) {
            scratchEngine.release()
        } else if (track != null) {
            scratchEngine.prepare(viewModelScope, track.uri)
        }
    }

    /**
     * Jog wheel touched in SCRATCH mode: hand the audio from the player to the scratch engine at the
     * current position. Returns false if scratching isn't possible (yet) — e.g. the track hasn't
     * been decoded up to the position.
     */
    fun startScratch(): Boolean {
        if (!isConnected || !isScratchModeEnabled) return false
        if (!scratchEngine.start(audioPlayer.positionMs, _jogwheelSensitivity.value)) {
            return false
        }
        wasPlayingBeforeScratch = _playerStatus.value == PlayerStatus.PLAYING
        audioPlayer.pause()
        startUpdateTimer()
        return true
    }

    fun scratchTo(rotationRad: Double) = scratchEngine.scratchTo(rotationRad)

    /**
     * Jog wheel released: the player takes over where the scratch ended, playing again only if it
     * was playing before.
     */
    fun endScratch() {
        if (!scratchEngine.isScratching) return
        val position = scratchEngine.stop()
        seekTo(position.toDouble(), false)
        if (wasPlayingBeforeScratch) audioPlayer.play() else resetUpdateTimer()
    }

    fun seekTo(position: Double, andStop: Boolean) {
        if (!isConnected) return

        audioPlayer.seekTo(position.toLong(), andStop)
        _onTrackPositionUpdate.value = position.toLong()
    }

    /**
     * Prev-button behavior: if more than [PREV_RESTART_THRESHOLD_MS] of the current track has
     * played, jump back to its start and return true; otherwise return false so the caller goes to
     * the previous track instead.
     */
    fun restartCurrentTrackIfPlayed(): Boolean {
        if (getTrackPosition() <= PREV_RESTART_THRESHOLD_MS) return false
        seekTo(0.0, false)
        return true
    }

    fun getTrackPosition(): Long {
        if (!isConnected) return 0

        return currentPositionMillis()
    }

    private fun currentPositionMillis() =
        if (scratchEngine.isScratching) scratchEngine.positionMs
        else audioPlayer.positionMs

    //
    // Private
    //

    fun saveState() {
        preferencesDataSource.savePitchFactorIndex(_pitchFactorIndex.value)
        preferencesDataSource.savePitchValue(currentPitchProgress)
    }

    private fun loadStates() {
        _pitchFactorIndex.value = preferencesDataSource.readPitchFactorIndex()
        pitchFactor = PITCH_RANGE_FACTORS[_pitchFactorIndex.value]
        _pitchValue.value = preferencesDataSource.readPitchValue()
        onPitchChanged(_pitchValue.value)
        showRemainingTrackTime = preferencesDataSource.readRemainingTimeMode()
        _showRemainingTime.value = showRemainingTrackTime
    }

    fun loadSettings() {
        autoStart = preferencesDataSource.isAutostartEnabled()
        _masterTempo.value = preferencesDataSource.readMasterTempoMode()
        isStopPlaybackOnSettingCuepointEnabled = preferencesDataSource.isStopPlaybackOnSettingCuepointEnabled()
        _jogwheelSensitivity.value = preferencesDataSource.getJogWheelSensitivity()
        _jogwheelHapticLevel.value = preferencesDataSource.getJogWheelHapticLevel()
        _jogwheelMode.value = preferencesDataSource.getJogWheelModeEnum()
        isScratchModeEnabled = _jogwheelMode.value == JogwheelMode.SCRATCH
        prepareScratch()
    }

    /** The jog wheel quick switch: the same setting as in Settings (applied via loadSettings). */
    fun setJogwheelMode(mode: JogwheelMode) {
        preferencesDataSource.setJogWheelMode(mode.ordinal)
    }

    private fun startUpdateTimer() {
        log.d { "startUpdateTimer()" }

        if (updateJob?.isActive == true) return

        updateJob = viewModelScope.launch {
            while (isActive) {
                _onTrackPositionUpdate.value = currentPositionMillis()
                // 250ms is plenty for the progress bar/time and keeps per-tick recomposition
                // from starving touch dispatch (which made seeking laggy during playback).
                // Faster while scratching, so the waveform follows the wheel.
                delay(if (scratchEngine.isScratching) 100 else 250)
            }
        }
    }

    private fun resetUpdateTimer() {
        updateJob?.cancel()
        updateJob = null
    }

    override fun onCleared() {
        scratchEngine.release()
        super.onCleared()
    }
}
