package org.qstuff.qplayer.player.mediaservice

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData

import timber.log.Timber

/**
 * The interface to the native (SuperpoweredSDK) side
 */
class QDeqPlayerSuperpowered : QDeqPlayer {

    companion object {

        init {
            System.loadLibrary("SuperpoweredInterface")
        }
    }

    private var isPlaying: Boolean = false
    private var samplerate = 44100
    private var buffersize = 512

    private var onPlayerStatusUpdate = MutableLiveData<Track>()
    private var onWaveFormDataUpdate = MutableLiveData<TrackData>()

    private lateinit var currentTrack: Track

    //
    // JNI/NDK
    //

    private external fun SuperpoweredNative(samplerate: Int, buffersize: Int)

    private external fun onPlayPause(play: Boolean)
    private external fun onSetTempo(value: Float, masterTempo: Boolean)
    private external fun onSetPosition(positionMs: Double, andStop: Boolean, synchronisedStart: Boolean)
    private external fun loadTrack(path: String)
    private external fun getPositionMs(): Long
    private external fun getDurationMs(): Long
    private external fun analyzeData(path: String): ByteArray
    private external fun destroyNative()

    private external fun jogTouchBegin(ticksForTurn: Int, scratchSlipMs: Int)
    private external fun jogTicks(value: Int, bendStretch: Boolean, bendMaxPercent: Float, bendHoldMs: Int, parameterMode: Boolean)
    private external fun jogTouchEnd(decelerate: Float, synchronisedStart: Boolean)


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
        currentTrack = track
        loadTrack(track.uri)
        getWaveFormData(track)
    }

    override fun loadTrackASync(track: Track) {
        currentTrack = track
        loadTrack(track.uri)
        getWaveFormData(track)
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

    override  fun getStatusObserver(): MutableLiveData<Track> = onPlayerStatusUpdate

    override  fun getWaveFormDataObserver(): MutableLiveData<TrackData> = onWaveFormDataUpdate

    private var processing = false;
    private val waveformQueue = arrayListOf<Track>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private fun getWaveFormData(track: Track) {
        Timber.v("getWaveFormData(): processing: $processing, $track")

        if (!processing && waveformQueue.isEmpty()) {
            waveformQueue.add(track)
            processAnalzyer()
        } else if (processing) {
            Timber.v("getWaveFormData(): adding to queue")
            waveformQueue.clear()
            waveformQueue.add(track)
        }
    }

    private fun processAnalzyer() {
        Timber.v("processAnalzyer(): queue: ${waveformQueue.size}")

        coroutineScope.launch {
            processing = true
            val track = waveformQueue.first()
            waveformQueue.removeAt(0)

            val trackData = TrackData(track, null)
            Timber.v("processAnalzyer(): ==========> $track")
            trackData.bytes = analyzeData(track.uri)
            Timber.v("onWaveFormDataUpdate(): <========== $track, samples: ${trackData.bytes?.size}")
            onWaveFormDataUpdate.postValue(trackData)
            processing = false

            if (!waveformQueue.isEmpty()) {
                Timber.v("processAnalzyer(): one more...")
                processAnalzyer()
            }
        }
    }

    //
    // Callbacks from the native side
    //

    fun onPrepared() {
        Timber.d("onPrepared()")
        currentTrack.trackStatus = Track.TrackStatus.PREPARED
        currentTrack.duration = getDurationMs()
        onPlayerStatusUpdate.postValue(currentTrack)
    }

    fun onCompletion() {
        Timber.d("onCompletion()")
        currentTrack.trackStatus = Track.TrackStatus.COMPLETED
        currentTrack.playPosition = 0
        onPlayerStatusUpdate.postValue(currentTrack)
    }

    fun onError() {
        Timber.d("onError()")
        currentTrack.trackStatus = Track.TrackStatus.ERROR
        onPlayerStatusUpdate.postValue(currentTrack)
    }
}
