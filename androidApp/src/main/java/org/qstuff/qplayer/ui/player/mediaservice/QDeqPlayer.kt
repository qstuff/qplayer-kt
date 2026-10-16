package org.qstuff.qplayer.ui.player.mediaservice

import android.content.Context
import androidx.media3.common.Player
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * Whether playback is ongoing (play requested and not idle/ended). Reflects the real player,
     * so it also follows play/pause from system media controls (notification, headset, …).
     */
    val playing: StateFlow<Boolean>

    /** The underlying Media3 player, handed to the MediaSession. */
    val media3Player: Player
}
