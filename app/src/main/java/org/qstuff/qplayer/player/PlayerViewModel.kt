package org.qstuff.qplayer.player

import android.app.Application
import android.content.*
import android.os.Handler
import android.os.IBinder
import androidx.lifecycle.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.player.mediaservice.QMediaPlayerService
import org.qstuff.qplayer.util.PlayerStatus
import timber.log.Timber

class  PlayerViewModel (application: Application):
        AndroidViewModel(application), KoinComponent {

    companion object {
        val PITCH_RANGE_FACTORS = floatArrayOf(62.5f, 33.3f, 10f, 5f)
    }

    // Observables
    lateinit var onWaveformDataUpdate: LiveData<TrackData>

    val trackStatus = MutableLiveData<Track>()
    val trackStatusMediator = MediatorLiveData<Track>()
    val playerStatus = MutableLiveData<PlayerStatus>()
    val playerStatusMediator = MediatorLiveData<PlayerStatus>()
    val onMediaServiceConnected = MediatorLiveData<Boolean>()
    val onTrackPositionUpdate = MutableLiveData<Long>()
    val pitchValueText = MutableLiveData<String>()
    val pitchValue = MutableLiveData<Int>()


    val masterTempo = MutableLiveData<Boolean>()

    var currentPitch: Int = 500
    var pitchFactor = PITCH_RANGE_FACTORS[0]

    // MediaService
    private lateinit var mediaService: QMediaPlayerService
    private var isMediaServiceRunning = false
    private var isMediaServiceBound = false
    private var pendingTrack: Track? = null

    // Update Task
    private var updateHandler = Handler()
    private var updateRunnable: Runnable? = null
    private var isUpdatetaskRunning = false

    private val preferencesDataSource by inject<PreferencesDataSource>()


    private val notificationBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.d("onReceive(): ")
            playPause()
        }
    }

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Timber.d("onServiceConnected(): ${name.toShortString()}")

            mediaService = (binder as QMediaPlayerService.MyBinder).service

            trackStatusMediator.addSource(mediaService.getStatusObserver()) { track ->
                trackStatusMediator.value = track
            }
            trackStatusMediator.addSource(trackStatus) { track ->
                trackStatusMediator.value = track
            }

           onWaveformDataUpdate = Transformations.map(mediaService.getWaveFormDataObserver()) { it }

            onMediaServiceConnected.value = true
            isMediaServiceRunning = true
            isMediaServiceBound = true

            if (pendingTrack != null) {
                this@PlayerViewModel.loadTrack(pendingTrack)
                pendingTrack = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.d("onServiceDisconnected(): ${name.toShortString()}")

            onMediaServiceConnected.value = false

            trackStatusMediator.removeSource(mediaService.getStatusObserver())
            trackStatusMediator.removeSource(trackStatus)

            // Destroy player
        }
    }

    init {
        playerStatus.value = PlayerStatus.PAUSED
        LocalBroadcastManager.getInstance(application)
                .registerReceiver(notificationBroadcastReceiver,
                        IntentFilter(QMediaPlayerService.NOT_ACTION_PLAYER_TOGGLED))

        // TODO: save & read all those from preferences
        masterTempo.value = false
        pitchValueText.value = "0,0%"

        loadStates()
    }

    //
    // Player User Interaction
    //

    fun playPause() {
        if (!isMediaServiceBound) return

        if (playerStatus.value == PlayerStatus.PLAYING) {

            mediaService.pause()
            playerStatus.value = PlayerStatus.PAUSED
            resetUpdateTimer()

        } else if (playerStatus.value == PlayerStatus.PAUSED) {

            mediaService.play()
            playerStatus.value = PlayerStatus.PLAYING
            startUpdateTimer()

        } else {
            Timber.w("playPause(): invalid player status: ${playerStatus.value}")
        }
    }

    fun toggleMasterTempo() {
        masterTempo.value = !(masterTempo.value ?: true)
    }

    fun onPitchRangeSelected(index: Int) {
        pitchFactor = PITCH_RANGE_FACTORS[index]
        saveStates()
    }

    fun onPitchChanged(progress: Int) {

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

        if (isMediaServiceRunning) {
            mediaService.setTrackSpeed(1.0f + diff / 100, masterTempo.value ?: false)
        }
    }

    //
    // MediaService
    //

    fun startMediaService() {

        if(!isMediaServiceRunning) {
            val app = getApplication<QDeqApplication>()
            val intent = Intent(app, QMediaPlayerService::class.java)
            app.startService(intent)
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
            Timber.d("loadTrack(): service not ready")
            pendingTrack = track
            return
        }

        if (track == null) {
            return
        }

        mediaService.pause()
        playerStatus.value = PlayerStatus.PAUSED

        mediaService.loadTrackASync(track)

        track.trackStatus = Track.TrackStatus.LOADING
        trackStatus.value = track
    }

    fun seekTo(position: Double, andStop: Boolean) {
        Timber.d("seekTo(): $position")

        mediaService.seekTo(position, andStop)
    }

    fun onTrackCompleted(track: Track) {
        playPause()

        // TODO: Continuous Play?
    }

    //
    // Private
    //

    private fun saveStates() {
        preferencesDataSource.savePitchFactor(pitchFactor)
    }

    private fun loadStates() {
        pitchFactor = preferencesDataSource.readPitchFactor()
    }

    private fun startUpdateTimer() {
        Timber.d("startUpdateTimer()")

        if (isUpdatetaskRunning) return

        updateHandler = Handler()
        updateRunnable = object : Runnable {
            override fun run() {
                onTrackPositionUpdate.value = mediaService.getCurrentPositionMillis()
                updateHandler.postDelayed(this, 500)
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
