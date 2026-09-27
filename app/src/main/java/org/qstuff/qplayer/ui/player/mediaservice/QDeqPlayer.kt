package org.qstuff.qplayer.ui.player.mediaservice

import android.content.Context
import kotlinx.coroutines.flow.SharedFlow
import org.qstuff.qplayer.datasource.model.Track

interface QDeqPlayer {

    fun create(context: Context)

    fun loadTrackSync(track: Track)
    fun loadTrackASync(track: Track)

    fun play()
    fun pause()
    fun stop()
    fun destroy()

    fun isPlaying(): Boolean
    fun isPaused(): Boolean

    fun setSpeed(factor: Float, masterTempo: Boolean)
    fun seekTo(position: Double, andStop: Boolean)

    fun getCurrentPositionMillis(): Long
    fun getDurationMillis(): Long

    /** Emits the current Track on each status transition (PREPARED / COMPLETED / ERROR). */
    val trackStatus: SharedFlow<Track>
}
