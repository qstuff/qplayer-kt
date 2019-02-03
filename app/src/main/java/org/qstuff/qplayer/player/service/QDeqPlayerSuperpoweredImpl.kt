package org.qstuff.qplayer.player.service

import android.content.Context
import android.media.AudioManager


import java.io.File

import timber.log.Timber

/**
 * Created by Claus Chierici (github@antamauna.net) on 2/19/15
 *
 * Copyright (C) 2015 Claus Chierici, All rights reserved.
 *
 * Wraps the standard Android MediaPlayer into QPlayerWrapper
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
    private val positionMs: Double
        external get
    private val durationMs: Double
        external get

    //private var playerController: PlayerController? = null
    private var loadedFile: File? = null

    val isPaused: Boolean
        get() = !isPlaying

    val currentPositionMillis: Double
        get() = positionMs

    val durationMillis: Double
        get() = durationMs

    //
    // JNI
    //

    external fun SuperpoweredNative(samplerate: Int, buffersize: Int)
    external fun onPlayPause(play: Boolean)
    external fun onSetTempo(value: Float, masterTempo: Boolean)
    external fun onSetPosition(positionMs: Double, andStop: Boolean, synchronisedStart: Boolean)
    external fun loadTrack(path: String)
    external fun analyzeData(path: String): ByteArray
    external fun destroyNative()

//    fun setPlayerController(@NonNull playerController: PlayerController) {
//        this.playerController = playerController
//    }

    //
    // QPlayerWrapper
    //

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

    override fun create(qPlayerEventListener: PitchControlListener, ctx: Context) {
        Timber.d("create():")

        // Get the device's sample rate and buffer size to enable low-latency Android audio output, if available.
        val samplerateString: String? = null
        val buffersizeString: String? = null

        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        samplerate = Integer.parseInt(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE))
        buffersize = Integer.parseInt(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER))

        SuperpoweredNative(samplerate, buffersize)
    }

    override fun destroy() {
        destroyNative()
        isPlaying = false
    }

    override fun setSpeed(factor: Float, mastertempo: Boolean) {
        onSetTempo(factor, mastertempo)
    }

    override fun seekTo(position: Double, andStop: Boolean) {
        onSetPosition(position, andStop, false)
    }

    override fun loadTrackSync(file: File) {
        loadTrack(file.absolutePath)
        loadedFile = file
    }

    override fun loadTrackASync(file: File) {
        loadTrack(file.absolutePath)
        loadedFile = file
    }

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
