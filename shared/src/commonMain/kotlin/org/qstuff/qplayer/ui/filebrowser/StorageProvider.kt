package org.qstuff.qplayer.ui.filebrowser

/**
 * A storage the user can browse. Android: internal storage, SD card; iOS later: the app's folder,
 * linked folders, the music library.
 */
data class StorageRoot(
    val id: String,
    val label: String,
    val isRemovable: Boolean
)

/**
 * An entry of a browsed folder. [id] identifies it for the [StorageProvider] (Android: the absolute
 * path) and is the track URI for tracks.
 */
data class BrowserItem(
    val id: String,
    val name: String,
    val kind: Kind
) {
    enum class Kind { FOLDER, PLAYLIST, TRACK }
}

/**
 * What the file browser can browse — platform independent. Android: the file system
 * (FileStorageProvider); iOS later: sandboxed folders and the music library.
 */
interface StorageProvider {

    /** The currently available storages (asked anew each time: an SD card may come and go). */
    fun roots(): List<StorageRoot>

    /** The storage [id] lies on, or null if it's on none of the current ones. */
    fun rootOf(id: String): StorageRoot?

    /**
     * The folder's supported entries in browser order (folders, playlists, tracks — each
     * alphabetically). Null if it isn't a folder or has no entries at all: the browser then stays
     * where it is.
     */
    fun list(folderId: String): List<BrowserItem>?

    /** The folder containing [id]; null if there is none. */
    fun parentOf(id: String): String?

    /** The folder's tracks (no playlists) in browser order — for adding a whole folder. */
    fun tracksIn(folderId: String): List<BrowserItem>

    /** The text content of a playlist file, or null if it can't be read. */
    fun readText(id: String): String?

    /** Whether a track referenced by a playlist exists. */
    fun exists(id: String): Boolean

    /** The folder as shown in the header: "<storage label><path below it>", e.g. "SD card/Music". */
    fun displayName(folderId: String): String
}
