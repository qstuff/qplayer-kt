package org.qstuff.qplayer.platform

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.qstuff.qplayer.util.AudioFiles
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorReadingWithoutChanges
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

private val log = Logger.withTag("IosFileImport")

/**
 * Files other apps hand to QdeQ ("Open in…", share sheet, AirDrop; the types are the
 * CFBundleDocumentTypes in iosApp/project.yml): each is copied into the Documents folder, so it
 * shows up in the file browser like a file added via the Files app or Finder.
 */
object IosFileImport {

    private const val INBOX = "Inbox"

    // One import at a time, so two files with the same name get different names.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    // Buffered until the UI collects it: an import may finish before the UI is up (cold start).
    private val imported = Channel<String>(Channel.UNLIMITED)

    /** Folder ids that files were just imported to (once each). */
    val importedTo: Flow<String> = imported.receiveAsFlow()

    /** Copies [url] into the Documents folder in the background, then reports it in [importedTo]. */
    fun import(url: NSURL) {
        scope.launch {
            val id = copyToDocuments(url) ?: return@launch
            imported.send(id.substringBeforeLast('/'))
        }
    }

    /** The id of the imported file, or null if it wasn't imported. */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun copyToDocuments(url: NSURL): String? {
        val name = url.lastPathComponent
        if (!url.fileURL || name == null || !AudioFiles.isSupported(name)) {
            log.w { "not importing $url: not a supported file" }
            return null
        }
        val documents = resolved(documentsDirectory())
        val source = url.path?.let(::resolved)
        val inbox = "$documents/$INBOX"
        val fromInbox = source != null && source.startsWith("$inbox/")

        // Opened from QdeQ's own folder in the Files app: already there.
        if (source != null && source.startsWith("$documents/") && !fromInbox) {
            return IosPaths.DOCUMENTS + source.removePrefix(documents)
        }

        val fileManager = NSFileManager.defaultManager
        val targetName = freeName(documents, name)
        val target = NSURL.fileURLWithPath("$documents/$targetName")

        // A file of another app or a file provider (iCloud Drive, …): access is granted only
        // while accessing; the coordinator downloads it first if needed.
        val accessing = url.startAccessingSecurityScopedResource()
        try {
            memScoped {
                val coordinatorError = alloc<ObjCObjectVar<NSError?>>()
                val copyError = alloc<ObjCObjectVar<NSError?>>()
                var copied = false
                NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(
                    url, options = NSFileCoordinatorReadingWithoutChanges, error = coordinatorError.ptr
                ) { readUrl ->
                    if (readUrl != null) copied = fileManager.copyItemAtURL(readUrl, toURL = target, error = copyError.ptr)
                }
                if (!copied) {
                    val error = copyError.value ?: coordinatorError.value
                    log.w { "can't import $url: ${error?.localizedDescription}" }
                    return null
                }
            }
        } finally {
            if (accessing) url.stopAccessingSecurityScopedResource()
        }

        // The system's copy in Documents/Inbox (it makes one for some sources): not needed any more,
        // and the Inbox folder would show up in the file browser.
        if (fromInbox) {
            fileManager.removeItemAtURL(url, error = null)
            if (fileManager.contentsOfDirectoryAtPath(inbox, error = null).isNullOrEmpty()) {
                fileManager.removeItemAtPath(inbox, error = null)
            }
        }

        log.i { "imported $name as $targetName" }
        return "${IosPaths.DOCUMENTS}/$targetName"
    }

    /** [name], or "<name> 2.<ext>", "<name> 3.<ext>", … if [folder] already has a file of that name. */
    private fun freeName(folder: String, name: String): String {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath("$folder/$name")) return name
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.')
        return generateSequence(2) { it + 1 }
            .map { "$base $it.$extension" }
            .first { !fileManager.fileExistsAtPath("$folder/$it") }
    }

    // The same folder can come as /var/… and /private/var/… (a symlink on devices).
    private fun resolved(path: String): String =
        NSURL.fileURLWithPath(path).URLByResolvingSymlinksInPath?.path ?: path
}

/** Called from Swift (onOpenURL in iosApp/iosApp/iOSApp.swift) for each file handed to the app. */
fun importSharedFile(url: NSURL) = IosFileImport.import(url)
