package org.qstuff.qplayer.equalizer

import android.app.Application
import androidx.lifecycle.*
import org.koin.standalone.KoinComponent

class  EqualizerViewModel (application: Application):
        AndroidViewModel(application), KoinComponent {

    val bandOneSliderValue = MutableLiveData<Int>()
    val bandTwoSliderValue = MutableLiveData<Int>()
    val bandThreeSliderValue = MutableLiveData<Int>()

    val bandOneDbTextValue = MutableLiveData<String>()
    val bandTwoDbTextValue = MutableLiveData<String>()
    val bandThreeDbTextValue = MutableLiveData<String>()


    fun onBandOneChanged(progress: Int) {

    }

    fun onBandTwoChanged(progress: Int) {

    }

    fun onBandThreeChanged(progress: Int) {

    }
}
