package org.qstuff.qplayer.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.ui.player.AudioPlayer
import org.qstuff.qplayer.ui.player.Scratcher
import org.qstuff.qplayer.ui.player.jogwheel.HapticFeedback
import org.qstuff.qplayer.ui.player.mediaservice.TransportCommand
import org.qstuff.qplayer.util.CrashReporter
import org.qstuff.qplayer.util.PlatformInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

// Placeholders for the iOS preview app — replaced one by one in Phase 2 (AVAudioEngine player, haptics, …).

/** The app's Documents folder: the user's music (visible in the Files app and in Finder). */
fun documentsDirectory(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String

/** The app's private data folder (Application Support, created if needed): the playlist database. */
@OptIn(ExperimentalForeignApi::class)
fun appDataDirectory(): String =
    NSFileManager.defaultManager.URLForDirectory(
        NSApplicationSupportDirectory, NSUserDomainMask, appropriateForURL = null, create = true, error = null
    )?.path ?: error("No Application Support directory")

/**
 * No audio yet: a loaded track is reported as prepared right away and play / pause only switch the
 * play state, so the UI can be tried out.
 */
class PreviewAudioPlayer : AudioPlayer {
    override val isConnected = MutableStateFlow(false)
    override val trackStatus = MutableSharedFlow<Track>(replay = 1, extraBufferCapacity = 8)
    override val playing: StateFlow<Boolean> = MutableStateFlow(false)
    override val transportCommands: SharedFlow<TransportCommand> = MutableSharedFlow()
    private var position = 0L

    override fun connect() { isConnected.value = true }
    override fun disconnect() { isConnected.value = false }
    override fun loadTrack(track: Track) {
        position = 0
        track.trackStatus = Track.TrackStatus.PREPARED
        trackStatus.tryEmit(track)
    }
    override fun play() { (playing as MutableStateFlow).value = true }
    override fun pause() { (playing as MutableStateFlow).value = false }
    override fun seekTo(positionMs: Long, andPause: Boolean) {
        position = positionMs
        if (andPause) pause()
    }
    override fun setSpeed(factor: Float, masterTempo: Boolean) {}
    override val positionMs: Long get() = position
    override val isPlaying: Boolean get() = playing.value
    override fun releaseForegroundHold() {}
}

class NoScratcher : Scratcher {
    override val isScratching = false
    override val positionMs = 0L
    override fun prepare(scope: CoroutineScope, uri: String) {}
    override fun release() {}
    override fun start(positionMs: Long, sensitivity: Int) = false
    override fun scratchTo(rotationRad: Double) {}
    override fun stop() = 0L
}

object NoHapticFeedback : HapticFeedback {
    override val isSupported = false
    override fun play(effect: HapticFeedback.Effect, strength: Int) {}
}

object NoCrashReporter : CrashReporter {
    override fun setEnabled(enabled: Boolean) {}
}

class IosPlatformInfo : PlatformInfo {
    override val appVersion: String = run {
        val info = NSBundle.mainBundle.infoDictionary
        "qdeq ${info?.get("CFBundleShortVersionString") ?: "?"} (${info?.get("CFBundleVersion") ?: "?"})"
    }
    override val deviceInfo: String = UIDevice.currentDevice.let { "${it.model} ${it.systemName} ${it.systemVersion}" }
}
