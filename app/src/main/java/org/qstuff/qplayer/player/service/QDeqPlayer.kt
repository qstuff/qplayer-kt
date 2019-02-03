package org.qstuff.qplayer.player.service

import android.content.Context

import java.io.File

interface QDeqPlayer {

    fun play()
    fun pause()
    fun stop()

    fun create(qPlayerEventListener: PitchControlListener?,
               ctx: Context)

    fun destroy()

    fun isPlaying(): Boolean
    fun isPaused(): Boolean
    fun setSpeed(factor: Float, masterTempo: Boolean)

    fun seekTo(position: Double, andStop: Boolean)
    fun getCurrentPositionMillis(): Double
    fun getDurationMillis(): Double

    fun loadTrackSync(file: File)
    fun loadTrackASync(file: File)

    fun getWaveformData(file: File)

//    fun setPlayerController(playerController: PlayerController)

}
