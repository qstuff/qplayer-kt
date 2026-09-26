package org.qstuff.qplayer.ui.player

import android.annotation.SuppressLint
import android.app.Application
import android.content.*
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.qstuff.qplayer.ui.player.waveform.WaveformAnalyzer
import org.qstuff.qplayer.util.PlayerStatus
import timber.log.Timber

class  PlayerViewModel (application: Application):
        AndroidViewModel(application), KoinComponent {

    companion object {
        const val ACTION_SERVICE_FOREGROUND_START = "ACTION_SERVICE_FOREGROUND_START"
        const val ACTION_SERVICE_FOREGROUND_STOP = "ACTION_SERVICE_FOREGROUND_STOP"
        const val ACTION_SERVICE_BACKGROUND_START = "ACTION_SERVICE_BACKGROUND_START"
        const val ACTION_SERVICE_BACKGROUND_STOP = "ACTION_SERVICE_BACKGROUND_STOP"

        val PITCH_RANGE_FACTORS = floatArrayOf(62.5f, 33.3f, 10f, 5f)
    }

    var mediaServiceStartMode: String
    var mediaServiceStopMode: String

    // onWaveformDataUpdate stays LiveData for now — it's bridged from the media service (Phase 3).
    val onWaveformDataUpdate = MediatorLiveData<TrackData?>()

    // Current track + status as an immutable snapshot; see PlayerTrackState. The revision makes
    // each emission distinct so re-selecting the same track still propagates through the equality
    // dedup of StateFlow / Compose State — no more version-counter observer in the UI.
    private val _playerTrackState = MutableStateFlow<PlayerTrackState?>(null)
    val playerTrackState: StateFlow<PlayerTrackState?> = _playerTrackState.asStateFlow()
    private var trackStateRevision = 0

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
    private var isProceedToNextTrackEnabled = false
    private var isSkipBackToStartEnabled = true
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

    // Bridges the media service's status LiveData → immutable PlayerTrackState.
    private var statusBridgeJob: Job? = null

    private val preferencesDataSource by inject<PreferencesDataSource>()


    private val notificationBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent != null) {
                val action = intent.action
                if (action != null) {

                    if (action == QMediaPlayerService.NOT_ACTION_PLAYER_TOGGLED) {
                        Timber.d("onReceive(): NOT_ACTION_PLAYER_TOGGLED")
                        playPause()
                    }
                    if (action == QMediaPlayerService.NOT_ACTION_NOTIFICATION_DISMISSED) {
                        Timber.d("onReceive(): NOT_ACTION_NOTIFICATION_DISMISSED")
                        mediaService.stop()
//                        mediaService.player.destroy()
                        stopMediaService()
                    }
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Timber.d("onServiceConnected(): ${name.toShortString()}")

            mediaService = (binder as QMediaPlayerService.MyBinder).service

            statusBridgeJob = viewModelScope.launch {
                mediaService.getStatusObserver().asFlow().collect { track ->
                    emitTrackState(track, track.trackStatus)
                }
            }

            onWaveformDataUpdate.addSource(mediaService.getWaveFormDataObserver()) { data ->
                onWaveformDataUpdate.value = data
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

            onWaveformDataUpdate.removeSource(mediaService.getWaveFormDataObserver())
            statusBridgeJob?.cancel()
        }
    }

    init {
        if (preferencesDataSource.isStartForegroundEnabled()) {
            mediaServiceStartMode = ACTION_SERVICE_FOREGROUND_START
            mediaServiceStopMode = ACTION_SERVICE_FOREGROUND_STOP
        } else {
            mediaServiceStartMode = ACTION_SERVICE_BACKGROUND_START
            mediaServiceStopMode = ACTION_SERVICE_BACKGROUND_STOP
        }

        LocalBroadcastManager.getInstance(application)
                .registerReceiver(notificationBroadcastReceiver,
                        IntentFilter(QMediaPlayerService.NOT_ACTION_PLAYER_TOGGLED))
        LocalBroadcastManager.getInstance(application)
                .registerReceiver(notificationBroadcastReceiver,
                        IntentFilter(QMediaPlayerService.NOT_ACTION_NOTIFICATION_DISMISSED))

        loadStates()
        loadSettings()
    }

    //
    // Player User Interaction
    //

    fun playPause() {

        if (!isMediaServiceBound) return

        when {
            _playerStatus.value == PlayerStatus.PLAYING -> {

                mediaService.pause()
                _playerStatus.value = PlayerStatus.PAUSED
                resetUpdateTimer()
            }
            _playerStatus.value == PlayerStatus.PAUSED -> {

                mediaService.play()
                _playerStatus.value = PlayerStatus.PLAYING
                startUpdateTimer()
            }
            else -> Timber.w("playPause(): invalid player status: ${_playerStatus.value}")
        }
    }

    fun playFromCue(track: Track?) {

        if (!isMediaServiceBound) return
        if (track == null) return

        mediaService.seekTo(track.cuePosition.toDouble(), true)
        mediaService.play()
        _playerStatus.value = PlayerStatus.PLAYING
        resetUpdateTimer()
        startUpdateTimer()
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

    fun startMediaService() {

        if(!isMediaServiceRunning) {
            val app = getApplication<QDeqApplication>()
            val intent = Intent(app, QMediaPlayerService::class.java)
            intent.action = mediaServiceStartMode
            app.startService(intent)
            app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun stopMediaService() {

        val app = getApplication<QDeqApplication>()
        val intent = Intent(app, QMediaPlayerService::class.java)
        intent.action = mediaServiceStopMode
        app.startService(intent)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

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

        val currentTrack = _playerTrackState.value?.track

        mediaService.pause()
        _playerStatus.value = PlayerStatus.PAUSED

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
    }

    /** Publish an immutable [PlayerTrackState] snapshot (distinct on every call via revision). */
    private fun emitTrackState(track: Track, status: Track.TrackStatus) {
        track.trackStatus = status
        _playerTrackState.value = PlayerTrackState(track, status, ++trackStateRevision)
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
            onWaveformDataUpdate.value = TrackData(track, cached)
            return
        }

        // Clear any previous waveform while the new one is decoding.
        onWaveformDataUpdate.value = null
        waveformJob?.cancel()
        waveformJob = viewModelScope.launch {
            val bytes = WaveformAnalyzer.analyze(getApplication(), uri)
            if (bytes != null && isActive) {
                waveformCache.put(uri, bytes)
                // Only publish if this is still the current track.
                if (_playerTrackState.value?.track?.uri == uri) {
                    onWaveformDataUpdate.value = TrackData(track, bytes)
                }
            }
        }
    }

    fun seekTo(position: Double, andStop: Boolean) {
        if (!isMediaServiceBound) return

        mediaService.seekTo(position, andStop)
        _onTrackPositionUpdate.value = position.toLong()
    }

    fun onTrackCompleted(track: Track) {
        Timber.d("onTrackCompleted(): ${track.name}, ${track.isAutoplay}")
        if (isProceedToNextTrackEnabled && autoStart) {
            Timber.d("onTrackCompleted(): proceed to next")
            resetUpdateTimer()
        } else {
            playPause()
        }
    }

    fun getTrackPosition(): Long {
        if (!isMediaServiceBound) return 0

        return mediaService.getCurrentPositionMillis()
    }

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
        isProceedToNextTrackEnabled = preferencesDataSource.isProceedToNextTrackEnabled()
        _masterTempo.value = preferencesDataSource.readMasterTempoMode()
        isSkipBackToStartEnabled = preferencesDataSource.isSkipBackToStartEnabled()
        isStopPlaybackOnSettingCuepointEnabled = preferencesDataSource.isStopPlaybackOnSettingCuepointEnabled()
        _jogwheelSensitivity.value = preferencesDataSource.getJogWheelSensitivity()
    }

    private fun startUpdateTimer() {
        Timber.d("startUpdateTimer()")

        if (isUpdatetaskRunning) return

        updateHandler = Handler(Looper.getMainLooper())
        updateRunnable = object : Runnable {
            override fun run() {
                _onTrackPositionUpdate.value = mediaService.getCurrentPositionMillis()
                // 250ms is plenty for the progress bar/time and keeps per-tick recomposition
                // from starving touch dispatch (which made seeking laggy during playback).
                updateHandler.postDelayed(this, 250)
            }
        }
        updateHandler.post(updateRunnable as Runnable)
        isUpdatetaskRunning = true
    }

    private fun resetUpdateTimer() {

        updateRunnable?.let { updateHandler.removeCallbacks(it) }
        isUpdatetaskRunning = false
    }
}
