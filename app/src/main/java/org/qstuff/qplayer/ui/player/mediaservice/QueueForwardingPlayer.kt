package org.qstuff.qplayer.ui.player.mediaservice

import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup

/**
 * The player handed to the MediaSession. Adds Next/Prev for system media controls (notification,
 * lock screen, headset and Bluetooth buttons) and routes them to the app's queue via [onNext] /
 * [onPrevious] — ExoPlayer itself only ever holds the current track, so it reports no "next".
 *
 * Listeners are wrapped as well: the session takes the available commands from the *argument* of
 * onAvailableCommandsChanged, and ForwardingPlayer passes ExoPlayer's raw commands through there,
 * which would drop the added skip commands on every track load. The wrapper forwards every
 * callback explicitly and only augments that one.
 */
class QueueForwardingPlayer(
    player: Player,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit
) : ForwardingPlayer(player) {

    private val listenerWrappers = HashMap<Player.Listener, Player.Listener>()

    override fun getAvailableCommands(): Player.Commands =
        withQueueCommands(super.getAvailableCommands())

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun seekToNext() = onNext()
    override fun seekToNextMediaItem() = onNext()
    override fun seekToPrevious() = onPrevious()
    override fun seekToPreviousMediaItem() = onPrevious()

    override fun addListener(listener: Player.Listener) {
        val wrapper = CommandAugmentingListener(listener)
        listenerWrappers[listener] = wrapper
        super.addListener(wrapper)
    }

    override fun removeListener(listener: Player.Listener) {
        listenerWrappers.remove(listener)?.let { super.removeListener(it) }
    }

    private fun withQueueCommands(commands: Player.Commands): Player.Commands =
        commands.buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
            .build()

    /** Forwards every callback unchanged, except that skip commands are added to the commands. */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    private inner class CommandAugmentingListener(
        private val listener: Player.Listener
    ) : Player.Listener {

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
            listener.onAvailableCommandsChanged(withQueueCommands(availableCommands))

        override fun onEvents(player: Player, events: Player.Events) =
            listener.onEvents(player, events)
        override fun onTimelineChanged(timeline: Timeline, reason: Int) =
            listener.onTimelineChanged(timeline, reason)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
            listener.onMediaItemTransition(mediaItem, reason)
        override fun onTracksChanged(tracks: Tracks) = listener.onTracksChanged(tracks)
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) =
            listener.onMediaMetadataChanged(mediaMetadata)
        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) =
            listener.onPlaylistMetadataChanged(mediaMetadata)
        override fun onIsLoadingChanged(isLoading: Boolean) = listener.onIsLoadingChanged(isLoading)
        override fun onLoadingChanged(isLoading: Boolean) = listener.onLoadingChanged(isLoading)
        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) =
            listener.onTrackSelectionParametersChanged(parameters)
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) =
            listener.onPlayerStateChanged(playWhenReady, playbackState)
        override fun onPlaybackStateChanged(playbackState: Int) =
            listener.onPlaybackStateChanged(playbackState)
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
            listener.onPlayWhenReadyChanged(playWhenReady, reason)
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) =
            listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)
        override fun onRepeatModeChanged(repeatMode: Int) = listener.onRepeatModeChanged(repeatMode)
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) =
            listener.onShuffleModeEnabledChanged(shuffleModeEnabled)
        override fun onPlayerError(error: PlaybackException) = listener.onPlayerError(error)
        override fun onPlayerErrorChanged(error: PlaybackException?) =
            listener.onPlayerErrorChanged(error)
        override fun onPositionDiscontinuity(reason: Int) = listener.onPositionDiscontinuity(reason)
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) = listener.onPositionDiscontinuity(oldPosition, newPosition, reason)
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) =
            listener.onPlaybackParametersChanged(playbackParameters)
        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) =
            listener.onSeekBackIncrementChanged(seekBackIncrementMs)
        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) =
            listener.onSeekForwardIncrementChanged(seekForwardIncrementMs)
        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) =
            listener.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
        override fun onVideoSizeChanged(videoSize: VideoSize) = listener.onVideoSizeChanged(videoSize)
        override fun onSurfaceSizeChanged(width: Int, height: Int) =
            listener.onSurfaceSizeChanged(width, height)
        override fun onRenderedFirstFrame() = listener.onRenderedFirstFrame()
        override fun onAudioSessionIdChanged(audioSessionId: Int) =
            listener.onAudioSessionIdChanged(audioSessionId)
        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) =
            listener.onAudioAttributesChanged(audioAttributes)
        override fun onVolumeChanged(volume: Float) = listener.onVolumeChanged(volume)
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) =
            listener.onSkipSilenceEnabledChanged(skipSilenceEnabled)
        override fun onCues(cues: List<Cue>) = listener.onCues(cues)
        override fun onCues(cueGroup: CueGroup) = listener.onCues(cueGroup)
        override fun onMetadata(metadata: Metadata) = listener.onMetadata(metadata)
        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) =
            listener.onDeviceInfoChanged(deviceInfo)
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) =
            listener.onDeviceVolumeChanged(volume, muted)
    }
}
