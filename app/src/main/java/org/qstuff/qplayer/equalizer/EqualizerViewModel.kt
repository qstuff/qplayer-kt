package org.qstuff.qplayer.equalizer

import android.app.Application
import androidx.lifecycle.*
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.datasource.mediaservice.MediaServiceDataSource
import org.qstuff.qplayer.datasource.mediaservice.QMediaPlayerService

class  EqualizerViewModel (application: Application):
        AndroidViewModel(application), KoinComponent {

    val bandOneSliderValue = MutableLiveData<Int>()
    val bandTwoSliderValue = MutableLiveData<Int>()
    val bandThreeSliderValue = MutableLiveData<Int>()

    val bandOneDbTextValue = MutableLiveData<String>()
    val bandTwoDbTextValue = MutableLiveData<String>()
    val bandThreeDbTextValue = MutableLiveData<String>()

    var bandOneFloatValue = 0f
    var bandTwoFloatValue = 0f
    var bandThreeFloatValue = 0f

    private val mediaServiceDataSource by inject<MediaServiceDataSource>()
    private var mediaService: QMediaPlayerService

    init {
        mediaService = mediaServiceDataSource.getMediaService()
    }

    fun onBandOneChanged(progress: Int) {
        if (mediaServiceDataSource.isMediaServiceBound()) {
            bandOneFloatValue = sliderToFloatValue(progress)
            mediaService?.set3BandEqValues(bandOneFloatValue, bandTwoFloatValue, bandThreeFloatValue)
        }
    }

    fun onBandTwoChanged(progress: Int) {
        if (mediaServiceDataSource.isMediaServiceBound()) {
            bandTwoFloatValue = sliderToFloatValue(progress)
            mediaService?.set3BandEqValues(bandOneFloatValue, bandTwoFloatValue, bandThreeFloatValue)
        }
    }

    fun onBandThreeChanged(progress: Int) {
        if (mediaServiceDataSource.isMediaServiceBound()) {
            bandThreeFloatValue = sliderToFloatValue(progress)
            mediaService?.set3BandEqValues(bandOneFloatValue, bandTwoFloatValue, bandThreeFloatValue)
        }
    }

    private fun sliderToFloatValue(sliderValue: Int) : Float {

        return 0f
    }

    // TODO: save / restore float and slider values

    // TODO: calculate db strings
}
