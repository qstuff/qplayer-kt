package org.qstuff.qplayer.ui.player.mediaservice

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.ui.player.PlayerActivity
import co.touchlab.kermit.Logger

private val log = Logger.withTag("QMediaPlayerService")

/**
 * Playback service, a Media3 [MediaSessionService]: the system shows a media-style notification
 * (also on the lock screen / in quick settings), routes headset and Bluetooth buttons to the
 * session, and keeps the service in the foreground while playback is ongoing. Media-session
 * notifications don't need the POST_NOTIFICATIONS runtime permission.
 *
 * The app talks to it in-process via [MyBinder] (bind with [ACTION_BIND_LOCAL]); Media3
 * controllers bind through the session intent and get the session binder.
 *
 * Created by Claus Chierici ( 3/2/17 )
 * Copyright (C) 2017
 * All rights reserved.
 */
@OptIn(UnstableApi::class)
class QMediaPlayerService : MediaSessionService() {

    companion object {
        /** Bind action for the app's own in-process binder; other bind intents go to Media3. */
        const val ACTION_BIND_LOCAL = "org.qstuff.qplayer.action.BIND_LOCAL_PLAYER_SERVICE"
    }

    /** Skip commands from system media controls, to be routed to the app's queue. */
    enum class TransportCommand { NEXT, PREVIOUS }

    lateinit var player: QDeqPlayer
    private val binder = MyBinder()
    private var mediaSession: MediaSession? = null

    private var currentTrack: Track? = null

    private val _transportCommands = MutableSharedFlow<TransportCommand>(extraBufferCapacity = 4)
    val transportCommands: SharedFlow<TransportCommand> = _transportCommands.asSharedFlow()

    /**
     * Keeps the service in the foreground across an auto-advance. Media3 drops the foreground
     * state when a track ENDS, and on Android 12+ a service can't re-enter the foreground from the
     * background when the next track then starts. So the hold is set the moment a track ends and
     * cleared once Media3 itself requires the foreground again (playback resumed), or via
     * [releaseForegroundHold] when playback doesn't continue.
     *
     * Note: Media3 applies async notification updates (artwork loading) without going through
     * [onUpdateNotification], i.e. without the hold. The track metadata deliberately has no
     * artwork — revisit this before adding any.
     */
    private var holdForeground = false

    //
    // Service Lifecycle
    //

    override fun onCreate() {
        super.onCreate()
        log.d { "onCreate()" }

        player = QDeqPlayerMedia3()
        player.create(this)

        // Registered before the session exists (so before the session's own listener): the hold
        // is in place before Media3 evaluates the ENDED state for its foreground decision.
        player.media3Player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) holdForeground = true
            }
        })

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(getString(R.string.notification_channel_id))
                .setChannelName(R.string.notification_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.qpl_status_bar_icon) }
        )

        val queuePlayer = QueueForwardingPlayer(
            player.media3Player,
            onNext = { _transportCommands.tryEmit(TransportCommand.NEXT) },
            onPrevious = { _transportCommands.tryEmit(TransportCommand.PREVIOUS) }
        )
        mediaSession = MediaSession.Builder(this, queuePlayer)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, PlayerActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
            // Register the session with the service's notification manager. Media3 only does this
            // automatically when a MediaController connects through the session interface — the
            // app uses the in-process binder instead, so without this there'd be no notification.
            .also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onBind(intent: Intent?): IBinder? {
        val sessionBinder = super.onBind(intent)
        return if (intent?.action == ACTION_BIND_LOCAL) binder else sessionBinder
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Media3 wants the foreground again because playback resumed: it maintains it from here.
        if (holdForeground && startInForegroundRequired && player.media3Player.isPlaying) {
            holdForeground = false
        }
        super.onUpdateNotification(session, startInForegroundRequired || holdForeground)
    }

    override fun onDestroy() {
        log.d { "onDestroy()" }
        mediaSession?.release()
        mediaSession = null
        player.destroy()
        super.onDestroy()
    }

    //
    // common public methods
    //

    /** Playback does not continue after a finished track: let the service leave the foreground. */
    fun releaseForegroundHold() {
        if (!holdForeground) return
        holdForeground = false
        mediaSession?.let { onUpdateNotification(it, /* startInForegroundRequired = */ false) }
    }

    val playing: StateFlow<Boolean> get() = player.playing

    val trackStatus: SharedFlow<Track> get() = player.trackStatus

    fun play() {
        log.d { "play():" }
        if (currentTrack == null) return
        player.play()
    }

    fun pause() {
        log.d { "pause():" }
        if (currentTrack == null) return
        player.pause()
    }

    fun stop() {
        player.stop()
    }

    fun isPlaying(): Boolean = player.isPlaying()

    fun setTrackSpeed(speedFactor: Float, masterTempo: Boolean) {
        player.setSpeed(speedFactor, masterTempo)
    }

    fun seekTo(position: Double, andStop: Boolean) {
        player.seekTo(position, andStop)
    }

    fun getCurrentPositionMillis(): Long = player.getCurrentPositionMillis()

    fun loadTrackASync(track: Track) {
        log.d { "loadTrackASync():" }
        currentTrack = track
        player.loadTrackASync(track)
    }

    //
    // Binder class for the in-process Service Connection
    //

    inner class MyBinder : Binder() {

        val service: QMediaPlayerService
            get() = this@QMediaPlayerService
    }
}
