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
    private val provider = PathStorageProvider(findRoots = { listOf(StorageRoot(root.path, "qdeq", isRemovable = false)) })

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
    fun `no list for files and missing folders, an empty one for empty folders`() {
        file("Music/a.mp3")
        file("Other/notes.txt")
        File(root, "Empty").mkdirs()

        assertNull(provider.list("${root.path}/Music/a.mp3"))
        assertNull(provider.list("${root.path}/missing"))
        assertEquals(emptyList<BrowserItem>(), provider.list("${root.path}/Empty"))
        assertEquals(emptyList<BrowserItem>(), provider.list("${root.path}/Other"))
    }

    @Test
    fun `virtual ids are mapped to the real files and kept in the results`() {
        file("ebm/a.mp3")
        file("ebm/Mix.m3u", "a.mp3\n")
        val virtual = PathStorageProvider(
            findRoots = { listOf(StorageRoot("/Documents", "qdeq", isRemovable = false)) },
            toFilePath = { id -> root.path + id.removePrefix("/Documents") }
        )

        assertEquals(listOf("/Documents/ebm"), virtual.list("/Documents")!!.map { it.id })
        assertEquals(listOf("/Documents/ebm/Mix.m3u", "/Documents/ebm/a.mp3"), virtual.list("/Documents/ebm")!!.map { it.id })
        assertEquals(listOf("/Documents/ebm/a.mp3"), virtual.tracksIn("/Documents/ebm").map { it.id })
        assertEquals("a.mp3\n", virtual.readText("/Documents/ebm/Mix.m3u"))
        assertTrue(virtual.exists("/Documents/ebm/a.mp3"))
        assertEquals("/Documents", virtual.parentOf("/Documents/ebm"))
        assertEquals("qdeq/ebm", virtual.displayName("/Documents/ebm"))
    }
}
