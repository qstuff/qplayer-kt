package org.qstuff.qplayer.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import androidx.lifecycle.*
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData
import org.qstuff.qplayer.player.service.QMediaPlayerService
import org.qstuff.qplayer.util.PlayerStatus
import timber.log.Timber

class  PlayerViewModel (application: Application): AndroidViewModel(application) {

    // Observables
    lateinit var onWaveformDataUpdate: LiveData<TrackData>
    val trackStatus = MutableLiveData<Track>()
    val trackStatusMediator = MediatorLiveData<Track>()

    val playerStatus = MutableLiveData<PlayerStatus>()
    val playerStatusMediator = MediatorLiveData<PlayerStatus>()

    val onMediaServiceConnected = MediatorLiveData<Boolean>()

    val onTrackPositionUpdate = MutableLiveData<Long>()

    private lateinit var mediaService: QMediaPlayerService

    private var isMediaServiceRunning = false
    private var isMediaServiceBound = false

    private var pendingTrack: Track? = null

    // Player Control
    var isTrackPlaying = false

    private var updateHandler = Handler()
    private var updateRunnable: Runnable? = null
    private var isUpdatetaskRunning = false


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

           onWaveformDataUpdate = Transformations.map(
                   mediaService.getWaveFormDataObserver()
           ) { it }

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
    // Trackhandling
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

        mediaService.playerServiceLoadTrackASync(track)

        track.trackStatus = Track.TrackStatus.LOADING
        trackStatus.value = track
    }

    fun seekTo(position: Double, andStop: Boolean) {
        Timber.d("seekTo(): $position")

        mediaService.playerServiceSeekTo(position, andStop)
    }

    fun trackCompleted() {
        playPause()

        // TODO: Continous Play?
    }

    //
    // Private
    //

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
