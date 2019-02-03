package org.qstuff.qplayer.player.service

import android.content.Context
import android.media.AudioManager

import java.io.File
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

    //private lateinit var playerController: PlayerController

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

//    fun setPlayerController(@NonNull playerController: PlayerController) {
//        this.playerController = playerController
//    }

    //
    // QPlayerWrapper
    //

    override fun create(ctx: Context) {
        Timber.d("create():")

        // Get the device's sample rate and buffer size to enable low-latency Android audio output, if available.
        val samplerateString: String? = null
        val buffersizeString: String? = null

        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        samplerate = Integer.parseInt(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE))
        buffersize = Integer.parseInt(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER))

        SuperpoweredNative(samplerate, buffersize)
    }

    override fun loadTrackSync(file: File) {
        loadTrack(file.absolutePath)
        //loadedFile = file
    }

    override fun loadTrackASync(file: File) {
        loadTrack(file.absolutePath)
        //loadedFile = file
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

    override fun getWaveformData(file: File) {
        Timber.d("getWaveformData() %s", file.name)

        val data = analyzeData(file.absolutePath)
//        playerController!!.onWaveformUpdateSubject.onNext(TrackData(data))
    }

    //
    // Callbacks from the native side
    //

    fun onPrepared() {
        Timber.d("onPrepared()")
/*
        if (playerController != null)
            playerController!!.onPreparedSubject.onNext(Track(loadedFile))
        else
            Timber.d("onPrepared(): playerController NULL")
*/
    }

    fun onCompletion() {
        Timber.d("onCompletion()")
/*
        if (playerController != null)
            playerController!!.onCompletionSubject.onNext(Track(loadedFile))
        else
            Timber.d("onCompletion(): playerController NULL")
*/
    }

    fun onError() {
        Timber.d("onError()")
/*
        if (playerController != null)
            playerController!!.onErrorSubject.onNext(Track(loadedFile))
        else
            Timber.d("onError(): playerController NULL")
*/
    }
}
