package org.qstuff.qplayer.ui.filebrowser

import org.qstuff.qplayer.util.fileBrowserOrder
import org.qstuff.qplayer.util.isM3UList
import org.qstuff.qplayer.util.isSupported
import org.qstuff.qplayer.util.listTracksForAddDialog
import java.io.File

/**
 * Android [StorageProvider] on the file system: ids are absolute paths. [findRoots] supplies the
 * mounted storages (StorageRoots.find, or fixed roots in tests).
 */
class FileStorageProvider(private val findRoots: () -> List<StorageRoot>) : StorageProvider {

    override fun roots(): List<StorageRoot> = findRoots()

    override fun rootOf(id: String): StorageRoot? =
        roots().firstOrNull { id == it.id || id.startsWith("${it.id}/") }

    override fun list(folderId: String): List<BrowserItem>? {
        val dir = File(folderId)
        if (!dir.isDirectory) return null
        val entries = dir.listFiles()
        if (entries.isNullOrEmpty()) return null
        return entries.filter { it.isSupported() }.sortedWith(fileBrowserOrder).map { it.toItem() }
    }

    override fun parentOf(id: String): String? = File(id).parent

    override fun tracksIn(folderId: String): List<BrowserItem> =
        File(folderId).listTracksForAddDialog().map { it.toItem() }

    override fun readText(id: String): String? = runCatching { File(id).readText() }.getOrNull()

    override fun exists(id: String): Boolean = File(id).isFile

    override fun displayName(folderId: String): String =
        rootOf(folderId)?.let { it.label + folderId.removePrefix(it.id) } ?: folderId

    private fun File.toItem() = BrowserItem(
        id = absolutePath,
        name = name,
        kind = when {
            isDirectory -> BrowserItem.Kind.FOLDER
            isM3UList() -> BrowserItem.Kind.PLAYLIST
            else -> BrowserItem.Kind.TRACK
        }
    )
}
