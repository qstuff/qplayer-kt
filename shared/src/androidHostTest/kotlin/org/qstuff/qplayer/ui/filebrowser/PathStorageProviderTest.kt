package org.qstuff.qplayer.ui.filebrowser

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PathStorageProviderTest {

    private val root = Files.createTempDirectory("storage").toFile()
    private val provider = PathStorageProvider { listOf(StorageRoot(root.path, "qdeq", isRemovable = false)) }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun file(path: String, text: String = "") =
        File(root, path).apply { parentFile.mkdirs(); writeText(text) }

    @Test
    fun `lists folders, playlists and tracks in browser order, without unsupported files`() {
        File(root, "Music/zeta").mkdirs()
        File(root, "Music/.hidden").mkdirs()
        file("Music/Track B.mp3")
        file("Music/track a.m4a")
        file("Music/Mix.m3u")
        file("Music/notes.txt")
        file("Music/.secret.mp3")

        val items = provider.list("${root.path}/Music")!!

        assertEquals(listOf("zeta", "Mix.m3u", "track a.m4a", "Track B.mp3"), items.map { it.name })
        assertEquals(
            listOf(BrowserItem.Kind.FOLDER, BrowserItem.Kind.PLAYLIST, BrowserItem.Kind.TRACK, BrowserItem.Kind.TRACK),
            items.map { it.kind }
        )
        assertEquals("${root.path}/Music/Mix.m3u", items[1].id)
    }

    @Test
    fun `tracks of a folder, playlist text, existence and names`() {
        file("Music/b.mp3")
        file("Music/a.wav")
        file("Music/Mix.m3u", "a.wav\n")
        val music = "${root.path}/Music"

        assertEquals(listOf("a.wav", "b.mp3"), provider.tracksIn(music).map { it.name })
        assertEquals("a.wav\n", provider.readText("$music/Mix.m3u"))
        assertTrue(provider.exists("$music/b.mp3"))
        assertFalse(provider.exists("$music/missing.mp3"))
        assertFalse(provider.exists(music))
        assertEquals("qdeq/Music", provider.displayName(music))
        assertEquals(root.path, provider.parentOf(music))
        assertEquals("qdeq", provider.rootOf(music)?.label)
    }

    @Test
    fun `no list for files, missing and empty folders`() {
        file("Music/a.mp3")
        File(root, "Empty").mkdirs()

        assertNull(provider.list("${root.path}/Music/a.mp3"))
        assertNull(provider.list("${root.path}/missing"))
        assertNull(provider.list("${root.path}/Empty"))
    }
}
