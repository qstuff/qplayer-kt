package org.qstuff.qplayer.player

import android.app.Application
import android.content.*
import android.os.Handler
import androidx.lifecycle.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.datasource.mediaservice.MediaServiceDataSource
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.datasource.mediaservice.QMediaPlayerService
import org.qstuff.qplayer.util.PlayerStatus
import timber.log.Timber

class  PlayerViewModel (application: Application):
        AndroidViewModel(application), KoinComponent {

    interface MediaServiceConnectionInfo {
        fun onMediaServiceConnected()
        fun onMediaServiceDisconnected()
        fun onPendingTrack(pendingTrack: Track)
    }

    companion object {
        val PITCH_RANGE_FACTORS = floatArrayOf(62.5f, 33.3f, 10f, 5f)
    }

    // Observables
    var onWaveformDataUpdate = MutableLiveData<TrackData>()
    val onMediaServiceConnected = MediatorLiveData<Boolean>()
    val trackStatus = MutableLiveData<Track>()
    val trackStatusMediator = MediatorLiveData<Track>()
    val playerStatus = MutableLiveData<PlayerStatus>()
    val onTrackPositionUpdate = MutableLiveData<Long>()
    val pitchValueText = MutableLiveData<String>()
    val pitchValue = MutableLiveData<Int>()
    val jogwheelSensitivity = MutableLiveData<Int>()
    val pitchFactorIndex = MutableLiveData<Int>()
    val masterTempo = MutableLiveData<Boolean>()
    val cueActive = MutableLiveData<Boolean>()
    val showRemainingTime = MutableLiveData<Boolean>()

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
    private lateinit var mediaService: QMediaPlayerService

    // Update Task
    private var updateHandler = Handler()
    private var updateRunnable: Runnable? = null
    private var isUpdatetaskRunning = false

    private val preferencesDataSource by inject<PreferencesDataSource>()
    private val mediaServiceDataSource by inject<MediaServiceDataSource>()


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
                        stopMediaService()
                    }
                }
            }
        }
    }


    init {
        mediaServiceDataSource.registerMediaServiceConnectionInfo(object : MediaServiceConnectionInfo{
            override fun onMediaServiceConnected() {
                mediaService = mediaServiceDataSource.getMediaService()
                onMediaServiceConnected.value = true

                playerStatus.value = PlayerStatus.PAUSED

                LocalBroadcastManager.getInstance(application)
                        .registerReceiver(notificationBroadcastReceiver,
                                IntentFilter(QMediaPlayerService.NOT_ACTION_PLAYER_TOGGLED))
                LocalBroadcastManager.getInstance(application)
                        .registerReceiver(notificationBroadcastReceiver,
                                IntentFilter(QMediaPlayerService.NOT_ACTION_NOTIFICATION_DISMISSED))

                pitchValueText.value = "0,0%"
                cueActive.value = false

                trackStatusMediator.addSource(mediaService.getStatusObserver()) { track ->
                    trackStatusMediator.value = track
                }
                trackStatusMediator.addSource(trackStatus) { track ->
                    trackStatus.value = track
                }

                onWaveformDataUpdate = mediaService.getWaveFormDataObserver()

                loadStates()
                loadSettings()
            }

            override fun onMediaServiceDisconnected() {
                onMediaServiceConnected.value = false
            }

            override fun onPendingTrack(pendingTrack: Track) {
                loadTrack(pendingTrack)

            }
        })
    }

    //
    // Player User Interaction
    //

    fun playPause() {

        if (!mediaServiceDataSource.isMediaServiceBound()) return

        when {
            playerStatus.value == PlayerStatus.PLAYING -> {

                mediaService.pause()
                playerStatus.value = PlayerStatus.PAUSED
                resetUpdateTimer()
            }
            playerStatus.value == PlayerStatus.PAUSED -> {

                mediaService.play()
                playerStatus.value = PlayerStatus.PLAYING
                startUpdateTimer()
            }
            else -> Timber.w("playPause(): invalid player status: ${playerStatus.value}")
        }
    }

    fun playFromCue(track: Track?) {

        if (!mediaServiceDataSource.isMediaServiceBound()) return
        if (track == null) return

        mediaService.seekTo(track.cuePosition.toDouble(), true)
        mediaService.play()
        playerStatus.value = PlayerStatus.PLAYING
        resetUpdateTimer()
        startUpdateTimer()
    }

    fun toggleMasterTempo() {
        masterTempo.value = !(masterTempo.value ?: true)
        preferencesDataSource.saveMasterTempoMode(masterTempo.value ?: false)
    }

    fun onPitchRangeSelected(index: Int) {

        pitchFactor = PITCH_RANGE_FACTORS[index]
        pitchFactorIndex.value = index
        preferencesDataSource.savePitchFactorIndex(index)

        val progress = ((currentTrackSpeed -1) * 100 * pitchFactor + 500)
        pitchValue.value = progress.toInt()
        onPitchChanged(progress.toInt())
    }

    fun onPitchChanged(progress: Int) {
        currentPitchProgress = progress

        val diff = (progress - 500).toFloat() / pitchFactor
        var pre = if (diff > 0) "+" else ""

        if (diff == 0f) {
            pre = "   "
        }
        if (diff < 10 && diff > 0) {
            pre = "  +"
        }
        if (diff > -10 && diff < 0) {
            pre = "  "
        }

        val pitch = String.format("$pre%02.01f", diff)
        if (diff in -99.0..99.0) {
            pitchValueText.value = "$pitch%"
        } else {
            pitchValueText.value = pitch
        }

        if (1.0f + diff / 100 < 0) {
            return
        }

        currentTrackSpeed = 1.0f + diff / 100

        if (mediaServiceDataSource.isMediaServiceRunning()) {
            mediaService.setTrackSpeed(currentTrackSpeed, masterTempo.value ?: false)
        }
    }

    fun toggleCue(track: Track, enable: Boolean) {
        cueActive.value = enable

        if (mediaServiceDataSource.isMediaServiceRunning()) {
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
        showRemainingTime.value = showRemainingTrackTime

        if (!mediaService.isPlaying()) {
            val trackPosition = onTrackPositionUpdate.value
            onTrackPositionUpdate.value = trackPosition
        }
        preferencesDataSource.saveRemainigTimeMode(showRemainingTrackTime)
    }

    //
    // MediaService
    //

    fun startMediaService() {
        mediaServiceDataSource.startMediaService(getApplication())
    }

    fun stopMediaService() {
        mediaServiceDataSource.stopMediaService(getApplication())
    }

    //
    // Track handling
    //

    fun loadTrack(track: Track?) {
        Timber.d("loadTrack(): $track")

        if (!mediaServiceDataSource.isMediaServiceBound()) {
            mediaServiceDataSource.pendingTrack = track
            return
        }

        if (track == null) {
            return
        }

        val currentTrack = trackStatusMediator.value

        mediaService.pause()
        playerStatus.value = PlayerStatus.PAUSED

        if (currentTrack?.uri == track.uri) {
            Timber.d("loadTrack(): same track: $track")

            currentTrack.trackStatus = Track.TrackStatus.PREPARED
            currentTrack.playPosition = 0
            currentTrack.isAutoplay = autoStart
            trackStatusMediator.value = currentTrack

        } else {
            Timber.d("loadTrack(): new track: $track")
            mediaService.loadTrackASync(track)
            track.trackStatus = Track.TrackStatus.LOADING
            trackStatusMediator.value = track
        }
    }

    fun seekTo(position: Double, andStop: Boolean) {
        if (!mediaServiceDataSource.isMediaServiceBound()) return

        mediaService.seekTo(position, andStop)
        onTrackPositionUpdate.value = position.toLong()
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
        if (!mediaServiceDataSource.isMediaServiceBound()) return 0

        return mediaService.getCurrentPositionMillis()
    }

    //
    // Private
    //

    fun saveState() {
        preferencesDataSource.savePitchFactorIndex(pitchFactorIndex.value ?: 0)
        preferencesDataSource.savePitchValue(currentPitchProgress)
    }

    private fun loadStates() {
        pitchFactorIndex.value = preferencesDataSource.readPitchFactorIndex()
        pitchFactor = PITCH_RANGE_FACTORS[pitchFactorIndex.value ?: 0]
        pitchValue.value = preferencesDataSource.readPitchValue()
        onPitchChanged(pitchValue.value!!)
        showRemainingTrackTime = preferencesDataSource.readRemainigTimeMode()
        showRemainingTime.value = showRemainingTrackTime
    }

    fun loadSettings() {
        autoStart = preferencesDataSource.isAutostartEnabled()
        isProceedToNextTrackEnabled = preferencesDataSource.isProceedToNextTrackEnabled()
        masterTempo.value = preferencesDataSource.readMasterTempoMode()
        isSkipBackToStartEnabled = preferencesDataSource.isSkipBackToStartEnabled()
        isStopPlaybackOnSettingCuepointEnabled = preferencesDataSource.isStopPlaybackOnSettingCuepointEnabled()
        jogwheelSensitivity.value = preferencesDataSource.getJogWheelSensitivity()
    }

    private fun startUpdateTimer() {
        Timber.d("startUpdateTimer()")

        if (isUpdatetaskRunning) return

        updateHandler = Handler()
        updateRunnable = object : Runnable {
            override fun run() {
                onTrackPositionUpdate.value = mediaService.getCurrentPositionMillis()
                updateHandler.postDelayed(this, 100)
            }
        }
        updateHandler.post(updateRunnable)
        isUpdatetaskRunning = true
    }

    private fun resetUpdateTimer() {

        updateHandler.removeCallbacks(updateRunnable)
        isUpdatetaskRunning = false
    }
}
