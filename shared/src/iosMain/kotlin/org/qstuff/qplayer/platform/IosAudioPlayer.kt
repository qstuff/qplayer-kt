package org.qstuff.qplayer.platform

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.MainScope
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
import org.qstuff.qplayer.ui.player.mediaservice.TransportCommand
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioPlayerNodeCompletionDataPlayedBack
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioUnitTimePitch
import platform.AVFAudio.AVAudioUnitVarispeed
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.darwin.NSObjectProtocol

private val log = Logger.withTag("IosAudioPlayer")

/**
 * iOS [AudioPlayer] on AVAudioEngine: player node → varispeed → time-pitch → output.
 *
 * - Pitch: without master tempo the varispeed unit changes speed and pitch together (vinyl), with
 *   master tempo the time-pitch unit changes only the speed.
 * - The file is scheduled from a frame on; seeking stops the node and schedules again from the new
 *   frame. The position is that frame plus the node's player time (source frames, independent of
 *   the speed units after it).
 * - Status events are delivered asynchronously (like Android's player listener): the ViewModel
 *   marks a track LOADING right after loadTrack, so PREPARED must come after that.
 * - Background audio (audio session "playback"), lock screen / headset controls and Now Playing
 *   info; an interruption (call, alarm) pauses.
 *
 * Called on the main thread; the node's completion callback is moved there too.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosAudioPlayer : AudioPlayer {

    override val isConnected = MutableStateFlow(false)

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

    private val main = MainScope()
    private val engine = AVAudioEngine()
    private val node = AVAudioPlayerNode()
    private val varispeed = AVAudioUnitVarispeed()
    private val timePitch = AVAudioUnitTimePitch()

    private var track: Track? = null
    private var file: AVAudioFile? = null
    private var sampleRate = 44_100.0
    private var lengthFrames = 0L

    /** File frame the scheduled segment starts at. */
    private var segmentStart = 0L
    /** Position while nothing is playing. */
    private var stoppedFrame = 0L
    /** Whether a segment from [segmentStart] is scheduled (playing or paused). */
    private var scheduled = false
    /** Incremented whenever a schedule is dropped: completions of older ones are ignored. */
    private var generation = 0

    private var speed = 1f
    private val observers = mutableListOf<NSObjectProtocol>()
    private val commandTargets = mutableListOf<Pair<platform.MediaPlayer.MPRemoteCommand, Any>>()

    init {
        engine.attachNode(node)
        engine.attachNode(varispeed)
        engine.attachNode(timePitch)
        timePitch.bypass = true
    }

    override fun connect() {
        if (isConnected.value) return
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)
        observeSystemEvents()
        registerRemoteCommands()
        isConnected.value = true
    }

    override fun disconnect() {
        if (!isConnected.value) return
        dropSchedule()
        _playing.value = false
        engine.stop()
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        commandTargets.forEach { (command, target) -> command.removeTarget(target) }
        commandTargets.clear()
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
        isConnected.value = false
    }

    override fun loadTrack(track: Track) {
        dropSchedule()
        _playing.value = false
        this.track = track
        stoppedFrame = 0

        val opened = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val url = if (track.uri.startsWith("/")) NSURL.fileURLWithPath(IosPaths.toFilePath(track.uri)) else NSURL.URLWithString(track.uri)
            val result = url?.let { AVAudioFile(forReading = it, error = error.ptr) }
            if (result == null) log.e { "can't open ${track.uri}: ${error.value?.localizedDescription}" }
            result
        }
        file = opened
        if (opened == null) {
            report(track, Track.TrackStatus.ERROR)
            return
        }

        sampleRate = opened.processingFormat.sampleRate
        lengthFrames = opened.length
        connectGraph(opened)
        track.duration = framesToMs(lengthFrames)
        report(track, Track.TrackStatus.PREPARED)
        updateNowPlaying()
    }

    override fun play() {
        val file = file ?: return
        if (!startEngine()) return
        if (!scheduled) schedule(file, stoppedFrame.coerceIn(0, lengthFrames))
        node.play()
        _playing.value = true
        updateNowPlaying()
    }

    override fun pause() {
        if (_playing.value) {
            stoppedFrame = currentFrame()
            node.pause()
        }
        _playing.value = false
        updateNowPlaying()
    }

    override fun seekTo(positionMs: Long, andPause: Boolean) {
        if (file == null) return
        val keepPlaying = _playing.value && !andPause
        dropSchedule()
        stoppedFrame = msToFrames(positionMs).coerceIn(0, lengthFrames)
        _playing.value = false
        if (keepPlaying) play() else updateNowPlaying()
    }

    override fun setSpeed(factor: Float, masterTempo: Boolean) {
        speed = factor
        if (masterTempo) {
            varispeed.rate = 1f
            timePitch.rate = factor.coerceIn(1f / 32, 32f)
            timePitch.bypass = false
        } else {
            varispeed.rate = factor.coerceIn(0.25f, 4f)
            timePitch.rate = 1f
            timePitch.bypass = true
        }
        updateNowPlaying()
    }

    override val positionMs: Long get() = framesToMs(currentFrame())

    override val isPlaying: Boolean get() = _playing.value

    // iOS has no foreground service to release.
    override fun releaseForegroundHold() {}

    private fun report(track: Track, status: Track.TrackStatus) {
        main.launch {
            track.trackStatus = status
            _trackStatus.emit(track)
        }
    }

    /** Player node → varispeed → time-pitch → mixer, in the file's format (rate, channels). */
    private fun connectGraph(file: AVAudioFile) {
        val format = file.processingFormat
        engine.stop()
        engine.disconnectNodeOutput(node)
        engine.disconnectNodeOutput(varispeed)
        engine.disconnectNodeOutput(timePitch)
        engine.connect(node, to = varispeed, format = format)
        engine.connect(varispeed, to = timePitch, format = format)
        engine.connect(timePitch, to = engine.mainMixerNode, format = format)
        engine.prepare()
        startEngine()
    }

    private fun startEngine(): Boolean {
        if (engine.running) return true
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            engine.startAndReturnError(error.ptr).also { started ->
                if (!started) log.e { "engine start failed: ${error.value?.localizedDescription}" }
            }
        }
    }

    private fun schedule(file: AVAudioFile, fromFrame: Long) {
        val frames = lengthFrames - fromFrame
        segmentStart = fromFrame
        scheduled = true
        val scheduledGeneration = ++generation
        if (frames <= 0) {
            main.launch { if (scheduledGeneration == generation) onCompleted() }
            return
        }
        node.scheduleSegment(
            file,
            startingFrame = fromFrame,
            frameCount = frames.toUInt(),
            atTime = null,
            completionCallbackType = AVAudioPlayerNodeCompletionDataPlayedBack
        ) { _ ->
            // Audio thread → main. Also called when the node is stopped: then generation changed.
            main.launch { if (scheduledGeneration == generation) onCompleted() }
        }
    }

    /** Stop the node and forget its schedule (its completion is ignored). */
    private fun dropSchedule() {
        if (scheduled) stoppedFrame = currentFrame()
        generation++
        node.stop()
        scheduled = false
    }

    private fun onCompleted() {
        val track = track ?: return
        scheduled = false
        generation++
        node.stop()
        stoppedFrame = lengthFrames
        _playing.value = false
        track.playPosition = 0
        report(track, Track.TrackStatus.COMPLETED)
        updateNowPlaying()
    }

    private fun currentFrame(): Long {
        if (!scheduled || !_playing.value) return stoppedFrame
        val nodeTime = node.lastRenderTime ?: return segmentStart
        val playerTime = node.playerTimeForNodeTime(nodeTime) ?: return segmentStart
        return (segmentStart + playerTime.sampleTime).coerceIn(0, lengthFrames)
    }

    private fun framesToMs(frames: Long) = (frames * 1000 / sampleRate).toLong()
    private fun msToFrames(ms: Long) = (ms * sampleRate / 1000).toLong()

    private fun observeSystemEvents() {
        val center = NSNotificationCenter.defaultCenter
        // A call, alarm or other app takes the audio: pause like a stopped deck.
        observers += center.addObserverForName(
            AVAudioSessionInterruptionNotification, `object` = null, queue = NSOperationQueue.mainQueue
        ) { note ->
            val type = (note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedIntegerValue
            if (type == AVAudioSessionInterruptionTypeBegan) pause()
        }
        // Output changed (headphones, AirPlay …): the engine stopped — restart and continue.
        observers += center.addObserverForName(
            AVAudioEngineConfigurationChangeNotification, `object` = engine, queue = NSOperationQueue.mainQueue
        ) { _ ->
            val wasPlaying = _playing.value
            dropSchedule()
            _playing.value = false
            val file = file ?: return@addObserverForName
            connectGraph(file)
            if (wasPlaying) play()
        }
    }

    private fun registerRemoteCommands() {
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        fun add(command: platform.MediaPlayer.MPRemoteCommand, handler: (platform.MediaPlayer.MPRemoteCommandEvent?) -> Boolean) {
            command.enabled = true
            val target = command.addTargetWithHandler { event ->
                if (handler(event)) MPRemoteCommandHandlerStatusSuccess else MPRemoteCommandHandlerStatusCommandFailed
            }
            commandTargets += command to target
        }
        add(center.playCommand) { play(); true }
        add(center.pauseCommand) { pause(); true }
        add(center.togglePlayPauseCommand) { if (_playing.value) pause() else play(); true }
        add(center.nextTrackCommand) { _transportCommands.tryEmit(TransportCommand.NEXT) }
        add(center.previousTrackCommand) { _transportCommands.tryEmit(TransportCommand.PREVIOUS) }
        add(center.changePlaybackPositionCommand) { event ->
            val position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime ?: return@add false
            seekTo((position * 1000).toLong(), andPause = false)
            true
        }
    }

    private fun updateNowPlaying() {
        val track = track ?: return
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to track.name,
            MPMediaItemPropertyPlaybackDuration to lengthFrames / sampleRate,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to currentFrame() / sampleRate,
            MPNowPlayingInfoPropertyPlaybackRate to if (_playing.value) speed.toDouble() else 0.0
        )
    }
}
