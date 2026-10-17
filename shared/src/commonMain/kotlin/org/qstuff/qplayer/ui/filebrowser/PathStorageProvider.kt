package org.qstuff.qplayer.ui.filebrowser

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import org.qstuff.qplayer.util.AudioFiles

/**
 * [StorageProvider] on the file system via kotlinx-io: ids are absolute paths. [findRoots] supplies
 * the storages (iOS: the app's Documents folder). Same rules and order as Android's
 * FileStorageProvider, except that empty folders can be opened.
 */
class PathStorageProvider(private val findRoots: () -> List<StorageRoot>) : StorageProvider {

    override fun roots(): List<StorageRoot> = findRoots()

    override fun rootOf(id: String): StorageRoot? =
        roots().firstOrNull { id == it.id || id.startsWith("${it.id}/") }

    override fun list(folderId: String): List<BrowserItem>? {
        val entries = children(folderId) ?: return null
        return entries.filter { it.isSupported() }.sortedWith(BROWSER_ORDER).map { it.toItem() }
    }

    override fun parentOf(id: String): String? = Path(id).parent?.toString()

    override fun tracksIn(folderId: String): List<BrowserItem> =
        children(folderId).orEmpty()
            .filter { it.isFile() && AudioFiles.isSupported(it.name) && !AudioFiles.isM3uPlaylist(it.name) }
            .sortedWith(BROWSER_ORDER)
            .map { it.toItem() }

    override fun readText(id: String): String? =
        runCatching { SystemFileSystem.source(Path(id)).buffered().use { it.readString() } }.getOrNull()

    override fun exists(id: String): Boolean = Path(id).isFile()

    override fun displayName(folderId: String): String =
        rootOf(folderId)?.let { it.label + folderId.removePrefix(it.id) } ?: folderId

    private fun children(folderId: String): List<Path>? {
        val dir = Path(folderId)
        if (SystemFileSystem.metadataOrNull(dir)?.isDirectory != true) return null
        return runCatching { SystemFileSystem.list(dir).toList() }.getOrNull()
    }

    private fun Path.isFile() = SystemFileSystem.metadataOrNull(this)?.isRegularFile == true
    private fun Path.isDirectory() = SystemFileSystem.metadataOrNull(this)?.isDirectory == true

    private fun Path.isSupported(): Boolean = when {
        isDirectory() -> !AudioFiles.isHidden(name)
        isFile() -> AudioFiles.isSupported(name)
        else -> false
    }

    private fun Path.toItem() = BrowserItem(
        id = toString(),
        name = name,
        kind = when {
            isDirectory() -> BrowserItem.Kind.FOLDER
            AudioFiles.isM3uPlaylist(name) -> BrowserItem.Kind.PLAYLIST
            else -> BrowserItem.Kind.TRACK
        }
    )

    private companion object {
        /** Folders, then M3U playlists, then audio files — each alphabetically, ignoring case. */
        val BROWSER_ORDER: Comparator<Path> = compareBy<Path> {
            when {
                SystemFileSystem.metadataOrNull(it)?.isDirectory == true -> 0
                AudioFiles.isM3uPlaylist(it.name) -> 1
                else -> 2
            }
        }.thenBy { it.name.lowercase() }
    }
}
