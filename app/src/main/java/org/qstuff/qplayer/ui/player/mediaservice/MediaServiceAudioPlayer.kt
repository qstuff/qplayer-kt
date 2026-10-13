package org.qstuff.qplayer.ui.player.mediaservice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.ui.player.AudioPlayer

private val log = Logger.withTag("MediaServiceAudioPlayer")

/**
 * Android [AudioPlayer]: binds [QMediaPlayerService] (Media3 ExoPlayer + MediaSession) and forwards
 * its state. The service only needs to be bound — Media3 itself starts it as a foreground service
 * while playback is ongoing. ACTION_BIND_LOCAL selects the in-process binder (other bind intents
 * are Media3 controller connections).
 */
class MediaServiceAudioPlayer(context: Context) : AudioPlayer {

    private val appContext = context.applicationContext

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Same buffering as the service's flow: status transitions are events, the latest is replayed.
    private val _trackStatus = MutableSharedFlow<Track>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val trackStatus: SharedFlow<Track> = _trackStatus.asSharedFlow()

    private val _playing = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _transportCommands = MutableSharedFlow<TransportCommand>(extraBufferCapacity = 4)
    override val transportCommands: SharedFlow<TransportCommand> = _transportCommands.asSharedFlow()

    private var service: QMediaPlayerService? = null
    private var isBound = false
    // Forwards the bound service's flows; replaced on every (re)connect.
    private var forwarding: CoroutineScope? = null

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            log.d { "onServiceConnected(): ${name.toShortString()}" }
            val connected = (binder as QMediaPlayerService.MyBinder).service
            service = connected

            forwarding?.cancel()
            forwarding = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).apply {
                launch { connected.trackStatus.collect { _trackStatus.emit(it) } }
                launch { connected.playing.collect { _playing.value = it } }
                launch { connected.transportCommands.collect { _transportCommands.emit(it) } }
            }
            _isConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            log.d { "onServiceDisconnected(): ${name.toShortString()}" }
            service?.let {
                it.stop()
                it.player.destroy()
            }
            dropService()
        }
    }

    override fun connect() {
        if (isBound) return
        val intent = Intent(appContext, QMediaPlayerService::class.java)
            .setAction(QMediaPlayerService.ACTION_BIND_LOCAL)
        isBound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun disconnect() {
        if (isBound) {
            appContext.unbindService(connection)
            isBound = false
        }
        if (service != null) appContext.stopService(Intent(appContext, QMediaPlayerService::class.java))
        dropService()
    }

    private fun dropService() {
        forwarding?.cancel()
        forwarding = null
        service = null
        _isConnected.value = false
        _playing.value = false
    }

    override fun loadTrack(track: Track) {
        service?.loadTrackASync(track)
    }

    override fun play() {
        service?.play()
    }

    override fun pause() {
        service?.pause()
    }

    override fun seekTo(positionMs: Long, andPause: Boolean) {
        service?.seekTo(positionMs.toDouble(), andPause)
    }

    override fun setSpeed(factor: Float, masterTempo: Boolean) {
        service?.setTrackSpeed(factor, masterTempo)
    }

    override val positionMs: Long get() = service?.getCurrentPositionMillis() ?: 0L

    override val isPlaying: Boolean get() = service?.isPlaying() ?: false

    override fun releaseForegroundHold() {
        service?.releaseForegroundHold()
    }
}
