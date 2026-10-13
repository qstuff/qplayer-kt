package org.qstuff.qplayer.util

/**
 * Which files the player lists and plays, decided by name only (no platform file API), so the same
 * rules apply wherever the files come from.
 */
object AudioFiles {

    private val SUPPORTED_EXTENSIONS = listOf(".mp3", ".mp4", ".m4a", ".aif", ".aac", ".wav", ".m3u")

    /** Hidden entries (".something") are never listed. */
    fun isHidden(name: String) = name.startsWith(".")

    fun isM3uPlaylist(name: String) = name.endsWith(".m3u")

    /** An audio file or M3U playlist the player can handle. */
    fun isSupported(name: String) = !isHidden(name) && SUPPORTED_EXTENSIONS.any { name.endsWith(it) }

    /** The last segment of a "/"-separated path. */
    fun nameOf(path: String) = path.substringAfterLast('/')
}
