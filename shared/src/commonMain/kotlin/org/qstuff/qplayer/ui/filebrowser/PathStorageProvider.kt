package org.qstuff.qplayer.ui.filebrowser

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import org.qstuff.qplayer.util.AudioFiles

/**
 * [StorageProvider] on the file system via kotlinx-io. [findRoots] supplies the storages (iOS: the
 * app's Documents folder). Same rules and order as Android's FileStorageProvider, except that
 * empty folders can be opened.
 *
 * Ids are "/"-separated paths below the roots' ids. [toFilePath] maps an id to the real file path —
 * on iOS the ids are virtual ("/Documents/…"), because the app container's real path changes with
 * every install and update, so stored ids (queue, playlists) must not contain it. By default ids
 * are the real paths.
 */
class PathStorageProvider(
    private val findRoots: () -> List<StorageRoot>,
    private val toFilePath: (id: String) -> String = { it }
) : StorageProvider {

    /** A folder entry: its id and its real path. */
    private class Entry(val id: String, val path: Path) {
        val name get() = path.name
        val isDirectory get() = SystemFileSystem.metadataOrNull(path)?.isDirectory == true
        val isFile get() = SystemFileSystem.metadataOrNull(path)?.isRegularFile == true
    }

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
            .filter { it.isFile && AudioFiles.isSupported(it.name) && !AudioFiles.isM3uPlaylist(it.name) }
            .sortedWith(BROWSER_ORDER)
            .map { it.toItem() }

    override fun readText(id: String): String? =
        runCatching { SystemFileSystem.source(path(id)).buffered().use { it.readString() } }.getOrNull()

    override fun exists(id: String): Boolean = SystemFileSystem.metadataOrNull(path(id))?.isRegularFile == true

    override fun displayName(folderId: String): String =
        rootOf(folderId)?.let { it.label + folderId.removePrefix(it.id) } ?: folderId

    private fun path(id: String) = Path(toFilePath(id))

    private fun children(folderId: String): List<Entry>? {
        val dir = path(folderId)
        if (SystemFileSystem.metadataOrNull(dir)?.isDirectory != true) return null
        return runCatching {
            SystemFileSystem.list(dir).map { Entry("${folderId.trimEnd('/')}/${it.name}", it) }
        }.getOrNull()
    }

    private fun Entry.isSupported(): Boolean = when {
        isDirectory -> !AudioFiles.isHidden(name)
        isFile -> AudioFiles.isSupported(name)
        else -> false
    }

    private fun Entry.toItem() = BrowserItem(
        id = id,
        name = name,
        kind = when {
            isDirectory -> BrowserItem.Kind.FOLDER
            AudioFiles.isM3uPlaylist(name) -> BrowserItem.Kind.PLAYLIST
            else -> BrowserItem.Kind.TRACK
        }
    )

    private companion object {
        /** Folders, then M3U playlists, then audio files — each alphabetically, ignoring case. */
        val BROWSER_ORDER: Comparator<Entry> = compareBy<Entry> {
            when {
                it.isDirectory -> 0
                AudioFiles.isM3uPlaylist(it.name) -> 1
                else -> 2
            }
        }.thenBy { it.name.lowercase() }
    }
}
