package org.qstuff.qplayer.player.service

import android.content.Context

import java.io.File

interface QDeqPlayer {

    fun create(qctx: Context)

    fun loadTrackSync(file: File)
    fun loadTrackASync(file: File)

    fun play()
    fun pause()
    fun stop()
    fun destroy()

    fun isPlaying(): Boolean
    fun isPaused(): Boolean

    fun setSpeed(factor: Float, masterTempo: Boolean)
    fun seekTo(position: Double, andStop: Boolean)

    fun getCurrentPositionMillis(): Double
    fun getDurationMillis(): Double

    fun getWaveformData(file: File)

//    fun setPlayerController(playerController: PlayerController)

}
