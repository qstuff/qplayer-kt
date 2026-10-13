package org.qstuff.qplayer.ui.player

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.ui.player.mediaservice.TransportCommand

/**
 * The playback engine as the player UI sees it — platform independent. Android: the Media3 media
 * service (MediaServiceAudioPlayer); iOS later: AVAudioEngine.
 *
 * Commands while not [isConnected] are ignored (playback can't happen yet); the caller decides
 * what to keep for later.
 */
interface AudioPlayer {

    /** Whether the engine is ready for commands (Android: the media service is bound). */
    val isConnected: StateFlow<Boolean>

    /** The loaded track on each status transition (PREPARED / COMPLETED / ERROR). */
    val trackStatus: SharedFlow<Track>

    /**
     * Whether audio is playing — the engine's real state, so it also follows play/pause from
     * system media controls (notification, lock screen, headset, …).
     */
    val playing: StateFlow<Boolean>

    /** Next / Prev from system media controls, to be routed to the queue. */
    val transportCommands: SharedFlow<TransportCommand>

    /** Start the engine (Android: bind the media service). */
    fun connect()

    /** Stop playback and shut the engine down. */
    fun disconnect()

    fun loadTrack(track: Track)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long, andPause: Boolean)

    /** [factor] 1.0 = normal; master tempo keeps the pitch, otherwise pitch follows speed (vinyl). */
    fun setSpeed(factor: Float, masterTempo: Boolean)

    val positionMs: Long
    val isPlaying: Boolean

    /**
     * Playback won't continue after the current track ended. Android: lets the media service leave
     * the foreground it held across track changes; elsewhere usually nothing to do.
     */
    fun releaseForegroundHold()
}
