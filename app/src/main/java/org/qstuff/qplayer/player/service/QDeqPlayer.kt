package org.qstuff.qplayer.player.service

import android.content.Context
import androidx.lifecycle.MutableLiveData
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData

import java.io.File

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

    fun getCurrentPositionMillis(): Double
    fun getDurationMillis(): Double

    fun getWaveformData(track: Track, onWaveformDataUpdate: MutableLiveData<TrackData>)

    fun setTrackStatusObserver(onPlayerStatusUpdate: MutableLiveData<Track>)
    fun getStatusObserver(): MutableLiveData<Track>
}
