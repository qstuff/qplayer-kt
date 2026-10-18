package org.qstuff.qplayer.platform

import org.qstuff.qplayer.util.CrashReporter
import org.qstuff.qplayer.util.PlatformInfo
import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioFile
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

// iOS folders and info, plus the remaining placeholder (crash reporting).

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

private val log = Logger.withTag("IosPlatform")

/**
 * Opens the audio file [id] (virtual path or URL) for reading; null if that isn't possible.
 *
 * The AVAudioFile(forReading:) initializer returns nil for a file it can't open (deleted, renamed,
 * an old queue entry, unsupported format), but a Kotlin constructor can't be null: the interop
 * throws a NullPointerException for that nil, which would crash the app if not caught here.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun openAudioFile(id: String): AVAudioFile? = memScoped {
    val url = (if (id.startsWith("/")) NSURL.fileURLWithPath(IosPaths.toFilePath(id)) else NSURL.URLWithString(id))
        ?: return null
    val error = alloc<ObjCObjectVar<NSError?>>()
    try {
        AVAudioFile(forReading = url, error = error.ptr)
    } catch (_: NullPointerException) {
        log.w { "can't open $id: ${error.value?.localizedDescription}" }
        null
    }
}

/** The app's private data folder (Application Support, created if needed): the playlist database. */
@OptIn(ExperimentalForeignApi::class)
fun appDataDirectory(): String =
    NSFileManager.defaultManager.URLForDirectory(
        NSApplicationSupportDirectory, NSUserDomainMask, appropriateForURL = null, create = true, error = null
    )?.path ?: error("No Application Support directory")

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
