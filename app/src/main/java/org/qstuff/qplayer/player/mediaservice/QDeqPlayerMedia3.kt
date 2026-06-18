package org.qstuff.qplayer.player.mediaservice

import android.content.Context
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData
import timber.log.Timber
import java.io.File

class QDeqPlayerMedia3 : QDeqPlayer {

    private lateinit var exoPlayer: ExoPlayer
    private val statusObserver = MutableLiveData<Track>()
    private val waveformObserver = MutableLiveData<TrackData>()
    private lateinit var currentTrack: Track
    private var preparedNotified = false

    override fun create(context: Context) {
        exoPlayer = ExoPlayer.Builder(context).build()
        exoPlayer.addListener(listener)
    }

    private val listener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    if (!preparedNotified && ::currentTrack.isInitialized) {
                        preparedNotified = true
                        currentTrack.trackStatus = Track.TrackStatus.PREPARED
                        currentTrack.duration = exoPlayer.duration
                        statusObserver.postValue(currentTrack)
                    }
                }
                Player.STATE_ENDED -> {
                    if (::currentTrack.isInitialized) {
                        currentTrack.trackStatus = Track.TrackStatus.COMPLETED
                        currentTrack.playPosition = 0
                        statusObserver.postValue(currentTrack)
                    }
                }
                else -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "onPlayerError()")
            if (::currentTrack.isInitialized) {
                currentTrack.trackStatus = Track.TrackStatus.ERROR
                statusObserver.postValue(currentTrack)
            }
        }
    }

    override fun loadTrackSync(track: Track) = loadTrack(track)
    override fun loadTrackASync(track: Track) = loadTrack(track)

    private fun loadTrack(track: Track) {
        currentTrack = track
        preparedNotified = false
        val uri = if (track.uri.startsWith("/")) Uri.fromFile(File(track.uri)) else Uri.parse(track.uri)
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    override fun play() = exoPlayer.play()
    override fun pause() = exoPlayer.pause()
    override fun stop() = exoPlayer.stop()
    override fun destroy() = exoPlayer.release()

    override fun isPlaying() = exoPlayer.isPlaying
    override fun isPaused() = !exoPlayer.isPlaying

    override fun setSpeed(factor: Float, masterTempo: Boolean) {
        // masterTempo=true  → time-stretch: speed changes, pitch stays at 1.0
        // masterTempo=false → vinyl: pitch scales with speed
        exoPlayer.playbackParameters = if (masterTempo) {
            PlaybackParameters(factor, 1.0f)
        } else {
            PlaybackParameters(factor, factor)
        }
    }

    override fun seekTo(position: Double, andStop: Boolean) {
        exoPlayer.seekTo(position.toLong())
        if (andStop) exoPlayer.pause()
    }

    override fun getCurrentPositionMillis() = exoPlayer.currentPosition
    override fun getDurationMillis() = exoPlayer.duration

    override fun getStatusObserver() = statusObserver

    // Waveform analysis not yet implemented — stubbed until a follow-up step
    override fun getWaveFormDataObserver() = waveformObserver
}
