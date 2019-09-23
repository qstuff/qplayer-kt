package org.qstuff.qplayer.equalizer

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

class  EqualizerViewModel (application: Application):
        AndroidViewModel(application), KoinComponent {

    val bandOneValue = MutableLiveData<Int>()
    val bandTwoValue = MutableLiveData<Int>()
    val bandThreeValue = MutableLiveData<Int>()

    fun onBandOneChanged(progress: Int) {

    }

    fun onBandTwoChanged(progress: Int) {

    }

    fun onBandThreeChanged(progress: Int) {

    }
}
