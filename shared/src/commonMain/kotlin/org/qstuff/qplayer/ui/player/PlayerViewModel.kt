package org.qstuff.qplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlin.time.TimeSource
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
import org.qstuff.qplayer.util.PlayerLayout
import org.qstuff.qplayer.util.LruCache
import org.qstuff.qplayer.util.PlayerStatus
import co.touchlab.kermit.Logger
import kotlin.math.roundToInt

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

    // Phone player arrangement (Settings), applied right away via loadSettings
    private val _playerLayout = MutableStateFlow(PlayerLayout.MODERN)
    val playerLayout: StateFlow<PlayerLayout> = _playerLayout.asStateFlow()

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
    // The playback speed (1 = original tempo), e.g. for the current BPM
    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    private var showRemainingTrackTime = true

    // Settings
    private var autoStart = false
    private var isStopPlaybackOnSettingCuepointEnabled = false

    // Playback engine. A track loaded before the engine is connected is loaded once it is.
    private val isConnected get() = audioPlayer.isConnected.value
    private var pendingTrack: Track? = null

    // Position timer (runs while playing / scratching)
    private var updateJob: Job? = null

    // Waveform overview generation (platform decoder, off the main thread); in-memory cache in
    // front of the generator's disk cache
    private val waveformCache = LruCache<String, Waveform>(32)
    private var waveformJob: Job? = null
    // Beat grids by track uri; a cached null = no steady beat found (detected once per track)
    private val beatGridCache = LruCache<String, BeatResult>(64)
    private var beatJob: Job? = null
    // Background generation for the track that is likely loaded next (see prefetchWaveform)
    private var prefetchTrack: Track? = null
    private var prefetchJob: Job? = null

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

    /**
     * The fine-tune buttons (‹ ›): 0.1 % per step — at least one unit of the 0..1000 pitch scale,
     * so at ±100 % (5 units per %) a step is 0.2 % instead of nothing.
     */
    fun pitchStepDown() = onPitchChanged((_pitchValue.value - pitchStep()).coerceAtLeast(0))

    fun pitchStepUp() = onPitchChanged((_pitchValue.value + pitchStep()).coerceAtMost(1000))

    private fun pitchStep() = (0.1f * pitchFactor).roundToInt().coerceAtLeast(1)

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
        _playbackSpeed.value = currentTrackSpeed

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
     * [onWaveformDataUpdate] — while decoding also the growing previews, so it fills in from the
     * left. Generated waveforms are also kept on disk by the platform's CachingWaveformGenerator.
     * Decoding runs on a background dispatcher; the previous job is cancelled when a new track is
     * loaded (a running prefetch waits for the new one), and stale results for an already-changed
     * track are discarded. viewModelScope cancels any in-flight job when the ViewModel is cleared.
     */
    private fun generateWaveform(track: Track) {
        val uri = track.uri
        waveformJob?.cancel()
        waveformJob = null

        beatJob?.cancel()
        val cached = waveformCache.get(uri)
        if (cached != null) {
            _onWaveformDataUpdate.value = cached.toTrackData(track, beatGridCache.get(uri)?.grid)
            detectBeats(track, cached)
            // Nothing to decode: a prefetch can go on, unless it is for this very track.
            if (prefetchTrack?.uri == uri) startPrefetch()
        } else {
            startWaveformJob(track)
            // The loaded track has priority: a running prefetch starts over after its waveform.
            startPrefetch()
        }
    }

    private fun startWaveformJob(track: Track) {
        val uri = track.uri

        // Clear any previous waveform while the new one is decoding.
        _onWaveformDataUpdate.value = null
        waveformJob = viewModelScope.launch {
            // Growing previews while decoding (background thread; StateFlow is thread-safe).
            val waveform = waveformGenerator.generate(uri) { preview ->
                if (isActive && _playerTrackState.value?.track?.uri == uri) {
                    _onWaveformDataUpdate.value = preview.toTrackData(track)
                }
            }
            if (waveform != null && isActive) {
                waveformCache.put(uri, waveform)
                // Only publish if this is still the current track.
                if (_playerTrackState.value?.track?.uri == uri) {
                    _onWaveformDataUpdate.value = waveform.toTrackData(track)
                    detectBeats(track, waveform)
                }
            }
        }
    }

    /**
     * Detect tempo and beats of the current [track] from its finished [waveform] (off the main
     * thread, once per track — the result, also "none found", is cached) and publish them with
     * the waveform.
     */
    private fun detectBeats(track: Track, waveform: Waveform) {
        val uri = track.uri
        if (beatGridCache.get(uri) != null) return
        beatJob?.cancel()
        // Too short to find a tempo: no background job at all.
        if (!BeatDetector.canDetect(waveform.detail, waveform.detailPeaksPerSecond)) {
            beatGridCache.put(uri, BeatResult(null))
            return
        }
        beatJob = viewModelScope.launch {
            val started = TimeSource.Monotonic.markNow()
            val grid = withContext(Dispatchers.Default) {
                BeatDetector.detect(waveform.detail, waveform.detailPeaksPerSecond)
            }
            log.i { "beats of ${track.name}: ${grid?.bpm ?: "none"} BPM in ${started.elapsedNow()}" }
            if (!isActive) return@launch
            beatGridCache.put(uri, BeatResult(grid))
            if (_playerTrackState.value?.track?.uri == uri) {
                _onWaveformDataUpdate.value = waveform.toTrackData(track, grid)
            }
        }
    }

    /**
     * Generate the waveform of [track] in the background without showing it — for the track that
     * is likely loaded next, so its waveform is there at once. Waits for the current track's
     * waveform first, so both don't compete for the CPU. A new call replaces a running prefetch;
     * loading a track restarts it after that track's waveform. null: no prefetch.
     */
    fun prefetchWaveform(track: Track?) {
        prefetchTrack = track
        startPrefetch()
    }

    private fun startPrefetch() {
        prefetchJob?.cancel()
        val uri = prefetchTrack?.uri ?: return
        if (uri == _playerTrackState.value?.track?.uri || waveformCache.get(uri) != null) return
        val current = waveformJob
        prefetchJob = viewModelScope.launch {
            current?.join()
            if (waveformCache.get(uri) != null) return@launch
            val waveform = waveformGenerator.generate(uri) ?: return@launch
            if (isActive) waveformCache.put(uri, waveform)
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
    //
    // Dragging the scrolling waveform (tablet): like grabbing the record. With the jog wheel in
    // SCRATCH mode (and the track prepared) it scratches audibly; otherwise a silent move — paused
    // while dragging, then on at the new position. 1:1 in track time either way.
    //

    private var waveformDragMs: Long? = null
    private var waveformDragStartMs = 0L
    private var wasPlayingBeforeWaveformDrag = false

    fun startWaveformDrag() {
        if (!isConnected || waveformDragMs != null || scratchEngine.isScratching) return
        if (startScratch()) return
        wasPlayingBeforeWaveformDrag = _playerStatus.value == PlayerStatus.PLAYING
        waveformDragStartMs = audioPlayer.positionMs
        waveformDragMs = waveformDragStartMs
        audioPlayer.pause()
        startUpdateTimer()
    }

    /** [offsetMs]: track time moved since the drag started (> 0 forward). */
    fun dragWaveform(offsetMs: Double) {
        if (scratchEngine.isScratching) {
            scratchEngine.scratchToOffset(offsetMs)
            return
        }
        if (waveformDragMs == null) return
        val duration = _playerTrackState.value?.track?.duration ?: 0L
        val target = (waveformDragStartMs + offsetMs.toLong()).coerceAtLeast(0L)
        waveformDragMs = if (duration > 0) target.coerceAtMost(duration) else target
    }

    fun endWaveformDrag() {
        if (scratchEngine.isScratching) {
            endScratch()
            return
        }
        val position = waveformDragMs ?: return
        waveformDragMs = null
        if (wasPlayingBeforeWaveformDrag) {
            seekTo(position.toDouble(), false)
            audioPlayer.play()
        } else {
            seekTo(position.toDouble(), true)
            resetUpdateTimer()
        }
    }

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
        waveformDragMs ?: if (scratchEngine.isScratching) scratchEngine.positionMs
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
        _playerLayout.value = preferencesDataSource.getPlayerLayout()
        isScratchModeEnabled = _jogwheelMode.value == JogwheelMode.SCRATCH
        prepareScratch()
    }

    // The jog wheel quick switches: the same settings as in Settings (applied via loadSettings).

    fun setJogwheelMode(mode: JogwheelMode) {
        preferencesDataSource.setJogWheelMode(mode.ordinal)
    }

    fun setJogwheelSensitivity(sensitivity: Int) {
        preferencesDataSource.setJogWheelSensitivity(sensitivity)
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

private fun Waveform.toTrackData(track: Track, beatGrid: BeatGrid? = null) =
    TrackData(track, overview, detail, detailPeaksPerSecond, beatGrid)

/** A beat detection result; [grid] null = no steady beat found. */
private class BeatResult(val grid: BeatGrid?)
