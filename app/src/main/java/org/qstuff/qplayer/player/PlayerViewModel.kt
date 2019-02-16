package org.qstuff.qplayer.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.*
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData
import org.qstuff.qplayer.player.service.QMediaPlayerService
import timber.log.Timber

class  PlayerViewModel (application: Application): AndroidViewModel(application) {

    // Observables
    lateinit var onWaveformDataUpdate: LiveData<TrackData>
    val onPlayerStatusUpdate = MutableLiveData<Track>()
    val onPlayerStatusMediator = MediatorLiveData<Track>()
    val onMediaServiceConnected = MediatorLiveData<Boolean>()

    private lateinit var mediaService: QMediaPlayerService

    private var isMediaServiceRunning = false
    private var isMediaServiceBound = false

    // Player Control
    var isTrackPlaying = false


    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Timber.d("onServiceConnected(): %s", name.toShortString())

            mediaService = (binder as QMediaPlayerService.MyBinder).service

            onPlayerStatusMediator.addSource(mediaService.getStatusObserver()) { track ->
                onPlayerStatusMediator.value = track
            }
            onPlayerStatusMediator.addSource(onPlayerStatusUpdate) { track ->
                onPlayerStatusMediator.value = track
            }

           onWaveformDataUpdate = Transformations.map(
                   mediaService.getWaveFormDataObserver()
           ) { it }

            onMediaServiceConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.d("onServiceDisconnected(): %s", name.toShortString())

            onMediaServiceConnected.value = false

            onPlayerStatusMediator.removeSource(mediaService.getStatusObserver())
            onPlayerStatusMediator.removeSource(onPlayerStatusUpdate)

            // Destroy player
        }
    }

    init {

    }

    //
    // Player User Interaction
    //

    fun playPause() {
        if (isTrackPlaying) {
            isTrackPlaying = false
            mediaService.pause()
        } else {
            isTrackPlaying = true
            mediaService.play()
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
            isMediaServiceRunning = true
            isMediaServiceBound = true
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

    fun loadTrack(track: Track) {
        mediaService.playerServiceLoadTrackASync(track)

        track.trackStatus = Track.TrackStatus.LOADING
        onPlayerStatusUpdate.value = track
    }
}
