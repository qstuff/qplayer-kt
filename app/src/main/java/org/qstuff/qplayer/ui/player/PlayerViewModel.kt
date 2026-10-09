package org.qstuff.qplayer.ui.player

import android.annotation.SuppressLint
import android.app.Application
import android.content.*
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.ui.player.mediaservice.QMediaPlayerService
import org.qstuff.qplayer.ui.player.scratch.ScratchEngine
import org.qstuff.qplayer.ui.player.waveform.WaveformAnalyzer
import org.qstuff.qplayer.util.JogwheelMode
import org.qstuff.qplayer.util.PlayerStatus
import timber.log.Timber

class  PlayerViewModel (application: Application):
        AndroidViewModel(application), KoinComponent {

    companion object {
        val PITCH_RANGE_FACTORS = floatArrayOf(62.5f, 33.3f, 10f, 5f)

        /** Prev restarts the current track if it has played longer than this, else goes back. */
        const val PREV_RESTART_THRESHOLD_MS = 3000L
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
    // activity forwards it to the queue to decide what follows. An event, not state — it must
    // fire once per completion, also while the app is in the background.
    private val _trackCompleted = MutableSharedFlow<Track>(extraBufferCapacity = 1)
    val trackCompleted: SharedFlow<Track> = _trackCompleted.asSharedFlow()
    private var trackStateRevision = 0

    // One-shot events: Next/Prev pressed in system media controls (notification, lock screen,
    // headset, Bluetooth). The activity routes them to the queue, like the on-screen buttons.
    private val _transportCommands =
        MutableSharedFlow<QMediaPlayerService.TransportCommand>(extraBufferCapacity = 4)
    val transportCommands: SharedFlow<QMediaPlayerService.TransportCommand> =
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
    private var currentTrackSpeed = 0.0f
    private var showRemainingTrackTime = true

    // Settings
    private var autoStart = false
    private var isStopPlaybackOnSettingCuepointEnabled = false

    // MediaService
    @SuppressLint("StaticFieldLeak")
    private lateinit var mediaService: QMediaPlayerService
    private var isMediaServiceRunning = false
    private var isMediaServiceBound = false
    private var pendingTrack: Track? = null

    // Update Task
    private var updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var isUpdatetaskRunning = false

    // Waveform overview generation (native MediaExtractor+MediaCodec, off the main thread)
    private val waveformCache = LruCache<String, ByteArray>(32)
    private var waveformJob: Job? = null

    // Jog wheel SCRATCH mode: takes over the audio from ExoPlayer while the wheel is touched.
    private val scratchEngine = ScratchEngine(application)
    private var isScratchModeEnabled = false
    private var wasPlayingBeforeScratch = false

    // Collectors of the media service's flows (track status, play state, system Next/Prev).
    private val serviceJobs = mutableListOf<Job>()

    private val preferencesDataSource by inject<PreferencesDataSource>()

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Timber.d("onServiceConnected(): ${name.toShortString()}")

            mediaService = (binder as QMediaPlayerService.MyBinder).service

            serviceJobs.forEach { it.cancel() }
            serviceJobs.clear()
            serviceJobs += viewModelScope.launch {
                mediaService.trackStatus.collect { track ->
                    emitTrackState(track, track.trackStatus)
                }
            }
            // The player's real play state is the source of truth for PLAYING/PAUSED and the
            // position timer — play/pause can also come from system media controls.
            serviceJobs += viewModelScope.launch {
                mediaService.playing.collect { playing ->
                    // While scratching ExoPlayer is paused on purpose; the deck keeps its state.
                    if (scratchEngine.isScratching) return@collect
                    _playerStatus.value = if (playing) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                    if (playing) startUpdateTimer() else resetUpdateTimer()
                }
            }
            serviceJobs += viewModelScope.launch {
                mediaService.transportCommands.collect { _transportCommands.tryEmit(it) }
            }

            isMediaServiceRunning = true
            isMediaServiceBound = true

            if (pendingTrack != null) {
                this@PlayerViewModel.loadTrack(pendingTrack)
                pendingTrack = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.d("onServiceDisconnected(): ${name.toShortString()}")

            mediaService.stop()
            mediaService.player.destroy()

            serviceJobs.forEach { it.cancel() }
            serviceJobs.clear()
        }
    }

    init {
        loadStates()
        loadSettings()
    }

    //
    // Player User Interaction
    //

    // Play/pause only command the player; playerStatus and the position timer follow the player's
    // real play state (see the `playing` collector in serviceConnection).

    fun playPause() {
        if (!isMediaServiceBound) return

        if (_playerStatus.value == PlayerStatus.PLAYING) mediaService.pause() else mediaService.play()
    }

    fun playFromCue(track: Track?) {

        if (!isMediaServiceBound) return
        if (track == null) return

        mediaService.seekTo(track.cuePosition.toDouble(), true)
        mediaService.play()
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
        _pitchValue.value = progress.toInt()
        onPitchChanged(progress.toInt())
    }

    fun onPitchChanged(progress: Int) {
        currentPitchProgress = progress

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

        val pitch = String.format("$pre%02.01f", diff)

        if (diff in -99.0..99.0) {
            _pitchValueText.value = "$pitch%"
        } else {
            _pitchValueText.value = pitch
        }

        if (1.0f + diff / 100 < 0) {
            return
        }

        currentTrackSpeed = 1.0f + diff / 100

        if (isMediaServiceRunning) {
            mediaService.setTrackSpeed(currentTrackSpeed, _masterTempo.value)
        }
    }

    fun toggleCue(track: Track, enable: Boolean) {
        _cueActive.value = enable

        if (isMediaServiceRunning) {
            if (enable) {
                if (isStopPlaybackOnSettingCuepointEnabled && mediaService.isPlaying()) {
                    playPause()
                }
                track.cuePosition = mediaService.getCurrentPositionMillis()
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
    // MediaService
    //

    // The service only needs to be bound: Media3 itself starts it as a foreground service while
    // playback is ongoing. ACTION_BIND_LOCAL selects the in-process binder (other bind intents are
    // Media3 controller connections).

    fun startMediaService() {

        if(!isMediaServiceRunning) {
            val app = getApplication<QDeqApplication>()
            val intent = Intent(app, QMediaPlayerService::class.java)
                .setAction(QMediaPlayerService.ACTION_BIND_LOCAL)
            app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun stopMediaService() {

        val app = getApplication<QDeqApplication>()

        if (isMediaServiceBound) {
            app.unbindService(serviceConnection)
            isMediaServiceBound = false
        }
        if (isMediaServiceRunning) {
            app.stopService(Intent(app, QMediaPlayerService::class.java))
            isMediaServiceRunning = false
        }
    }

    //
    // Track handling
    //

    fun loadTrack(track: Track?) {
        Timber.d("loadTrack(): $track")

        if (!isMediaServiceBound) {
            pendingTrack = track
            return
        }

        if (track == null) {
            return
        }

        endScratch()

        val currentTrack = _playerTrackState.value?.track

        mediaService.pause()

        if (currentTrack?.uri == track.uri) {
            Timber.d("loadTrack(): same track: $track")

            currentTrack.playPosition = 0
            currentTrack.isAutoplay = autoStart
            emitTrackState(currentTrack, Track.TrackStatus.PREPARED)

        } else {
            Timber.d("loadTrack(): new track: $track")
            mediaService.loadTrackASync(track)
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
        if (!isMediaServiceBound) return
        seekTo(track.playPosition.toDouble(), false)
        if (track.isAutoplay) {
            mediaService.play()
        } else {
            // Not continuing (e.g. auto-advanced with autostart off): drop the foreground hold
            // the service took when the previous track ended.
            mediaService.releaseForegroundHold()
        }
    }

    /**
     * The current track finished: stop playback (the play-state flow then stops the timer) and
     * announce it via [trackCompleted]. Whether (and how) a following track is loaded is the
     * queue's decision — a loaded track that should auto-play starts via [onTrackPrepared].
     */
    private fun onTrackCompleted(track: Track) {
        if (isMediaServiceBound) mediaService.pause()
        track.playPosition = 0
        _trackCompleted.tryEmit(track)
    }

    /**
     * Nothing follows a finished track: rewind it (so Play starts it again) and let the service
     * leave the foreground.
     */
    fun stopAfterCompletion() {
        if (!isMediaServiceBound) return
        seekTo(0.0, true)
        mediaService.releaseForegroundHold()
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
            val bytes = WaveformAnalyzer.analyze(getApplication(), uri)
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
     * Jog wheel touched in SCRATCH mode: hand the audio from ExoPlayer to the scratch engine at the
     * current position. Returns false if scratching isn't possible (yet) — e.g. the track hasn't
     * been decoded up to the position.
     */
    fun startScratch(): Boolean {
        if (!isMediaServiceBound || !isScratchModeEnabled) return false
        if (!scratchEngine.start(mediaService.getCurrentPositionMillis(), _jogwheelSensitivity.value)) {
            return false
        }
        wasPlayingBeforeScratch = _playerStatus.value == PlayerStatus.PLAYING
        mediaService.pause()
        startUpdateTimer()
        return true
    }

    fun scratchTo(rotationRad: Double) = scratchEngine.scratchTo(rotationRad)

    /**
     * Jog wheel released: ExoPlayer takes over where the scratch ended, playing again only if it
     * was playing before.
     */
    fun endScratch() {
        if (!scratchEngine.isScratching) return
        val position = scratchEngine.stop()
        seekTo(position.toDouble(), false)
        if (wasPlayingBeforeScratch) mediaService.play() else resetUpdateTimer()
    }

    fun seekTo(position: Double, andStop: Boolean) {
        if (!isMediaServiceBound) return

        mediaService.seekTo(position, andStop)
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
        if (!isMediaServiceBound) return 0

        return currentPositionMillis()
    }

    private fun currentPositionMillis() =
        if (scratchEngine.isScratching) scratchEngine.positionMs
        else mediaService.getCurrentPositionMillis()

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
        isScratchModeEnabled = preferencesDataSource.getJogWheelModeEnum() == JogwheelMode.SCRATCH
        prepareScratch()
    }

    private fun startUpdateTimer() {
        Timber.d("startUpdateTimer()")

        if (isUpdatetaskRunning) return

        updateHandler = Handler(Looper.getMainLooper())
        updateRunnable = object : Runnable {
            override fun run() {
                _onTrackPositionUpdate.value = currentPositionMillis()
                // 250ms is plenty for the progress bar/time and keeps per-tick recomposition
                // from starving touch dispatch (which made seeking laggy during playback).
                // Faster while scratching, so the waveform follows the wheel.
                updateHandler.postDelayed(this, if (scratchEngine.isScratching) 100 else 250)
            }
        }
        updateHandler.post(updateRunnable as Runnable)
        isUpdatetaskRunning = true
    }

    private fun resetUpdateTimer() {

        updateRunnable?.let { updateHandler.removeCallbacks(it) }
        isUpdatetaskRunning = false
    }

    override fun onCleared() {
        scratchEngine.release()
        super.onCleared()
    }
}
