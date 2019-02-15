package org.qstuff.qplayer.player.service

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.MutableLiveData
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData

import timber.log.Timber

/**
 * The interface to the native (SuperpoweredSDK) side
 */
class QDeqPlayerSuperpoweredImpl : QDeqPlayer {

    companion object {

        init {
            System.loadLibrary("SuperpoweredExample")
        }
    }

    private var isPlaying: Boolean = false
    private var samplerate = 44100
    private var buffersize = 512

    private var onPlayerStatusUpdate: MutableLiveData<Track> = MutableLiveData()
    private lateinit var currentTrack: Track

    //
    // JNI/NDK
    //

    private external fun SuperpoweredNative(samplerate: Int, buffersize: Int)

    private external fun onPlayPause(play: Boolean)
    private external fun onSetTempo(value: Float, masterTempo: Boolean)
    private external fun onSetPosition(positionMs: Double, andStop: Boolean, synchronisedStart: Boolean)
    private external fun loadTrack(path: String)
    private external fun getPositionMs(): Double
    private external fun getDurationMs(): Double
    private external fun analyzeData(path: String): ByteArray
    private external fun destroyNative()

    //
    // QPlayerWrapper
    //

    override fun create(context: Context) {
        Timber.d("create():")

        // Get the device's sample rate and buffer size to enable low-latency Android audio output, if available.
        val samplerateString: String? = null
        val buffersizeString: String? = null

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        samplerate = Integer.parseInt(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE))
        buffersize = Integer.parseInt(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER))

        SuperpoweredNative(samplerate, buffersize)
    }

    override fun loadTrackSync(track: Track) {
        loadTrack(track.uri)
        currentTrack = track
    }

    override fun loadTrackASync(track: Track) {
        loadTrack(track.uri)
        currentTrack = track
    }

    override fun play() {
        Timber.d("play():")
        onPlayPause(true)
        isPlaying = true
    }

    override fun pause() {
        Timber.d("pause():")
        onPlayPause(false)
        isPlaying = false
    }

    override fun stop() {
        Timber.d("stop():")
        isPlaying = false
    }

    override fun destroy() {
        destroyNative()
        isPlaying = false
    }

    override fun isPlaying() = isPlaying

    override fun isPaused() = !isPlaying

    override fun setSpeed(factor: Float, masterTempo: Boolean) =  onSetTempo(factor, masterTempo)

    override fun seekTo(position: Double, andStop: Boolean) {
        onSetPosition(position, andStop, false)
    }

    override fun getCurrentPositionMillis() = getPositionMs()

    override fun getDurationMillis() = getDurationMs()

    override fun getWaveformData(track: Track, onWaveformDataUpdate: MutableLiveData<TrackData>) {
        Timber.d("getWaveformData() %s", track.name)

        onWaveformDataUpdate.postValue(TrackData(analyzeData(track.uri)))
    }

    override fun setTrackStatusObserver(onPlayerStatusUpdate: MutableLiveData<Track>) {
        this.onPlayerStatusUpdate = onPlayerStatusUpdate
    }

    override  fun getStatusObserver(): MutableLiveData<Track> = onPlayerStatusUpdate

    //
    // Callbacks from the native side
    //

    fun onPrepared() {
        Timber.d("onPrepared()")
        currentTrack.trackStatus = Track.TrackStatus.PREPARED
        onPlayerStatusUpdate.postValue(currentTrack)
    }

    fun onCompletion() {
        Timber.d("onCompletion()")
        currentTrack.trackStatus = Track.TrackStatus.COMPLETED
        onPlayerStatusUpdate.postValue(currentTrack)
    }

    fun onError() {
        Timber.d("onError()")
        currentTrack.trackStatus = Track.TrackStatus.ERROR
        onPlayerStatusUpdate.postValue(currentTrack)
    }
}
