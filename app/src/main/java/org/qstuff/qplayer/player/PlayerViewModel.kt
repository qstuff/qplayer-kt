package org.qstuff.qplayer.player

import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData
import org.qstuff.qplayer.player.service.QMediaPlayerService
import timber.log.Timber

class  PlayerViewModel (application: Application): AndroidViewModel(application) {

    // Observables
    val onWaveformDataUpdate: MutableLiveData<TrackData> = MutableLiveData()
    val onPlayerStatusUpdate: MutableLiveData<Track> = MutableLiveData()

    private lateinit var mediaService: QMediaPlayerService

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Timber.d("onServiceConnected(): %s", name.toShortString())

            mediaService = (binder as QMediaPlayerService.MyBinder).service
            mediaService.player.setTrackStatusObserver(onPlayerStatusUpdate)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.d("onServiceDisconnected(): %s", name.toShortString())

            // Destroy player
        }
    }
}
