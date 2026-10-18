package org.qstuff.qplayer.platform

import org.qstuff.qplayer.ui.player.Scratcher
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

// iOS folders and info, plus the remaining placeholders (scratching: Phase 3; crash reporting).

/** The app's Documents folder: the user's music (visible in the Files app and in Finder). */
fun documentsDirectory(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String

/**
 * Track and folder ids on iOS are virtual paths: the app container's real path changes with every
 * install and app update, so stored ids (queue, playlists, last folder) must not contain it.
 */
object IosPaths {
    /** Id of the Documents folder; its files are "/Documents/…". */
    const val DOCUMENTS = "/Documents"

    /** The real file path of a virtual id (other ids are taken as real paths). */
    fun toFilePath(id: String): String = when {
        id == DOCUMENTS || id.startsWith("$DOCUMENTS/") -> documentsDirectory() + id.removePrefix(DOCUMENTS)
        else -> id
    }
}

/** The app's private data folder (Application Support, created if needed): the playlist database. */
@OptIn(ExperimentalForeignApi::class)
fun appDataDirectory(): String =
    NSFileManager.defaultManager.URLForDirectory(
        NSApplicationSupportDirectory, NSUserDomainMask, appropriateForURL = null, create = true, error = null
    )?.path ?: error("No Application Support directory")

class NoScratcher : Scratcher {
    override val isScratching = false
    override val positionMs = 0L
    override fun prepare(scope: CoroutineScope, uri: String) {}
    override fun release() {}
    override fun start(positionMs: Long, sensitivity: Int) = false
    override fun scratchTo(rotationRad: Double) {}
    override fun stop() = 0L
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
