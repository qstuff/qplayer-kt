package org.qstuff.qplayer.player.mediaservice

import android.content.Context
import androidx.lifecycle.MutableLiveData
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData

interface QDeqPlayer {

    fun create(qctx: Context)

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

    fun getStatusObserver(): MutableLiveData<Track>
    fun getWaveFormDataObserver(): MutableLiveData<TrackData>

    fun onJogTouchBegin(ticksForTurn: Int, scratchSlipMs: Int)
    fun onJogTicks(value: Int, bendStretch: Boolean, bendMaxPercent: Float, bendHoldMs: Int, parameterMode: Boolean)
    fun onJogTouchEnd(decelerate: Float, synchronisedStart: Boolean)
}
