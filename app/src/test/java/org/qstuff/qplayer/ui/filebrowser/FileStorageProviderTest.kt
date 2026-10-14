package org.qstuff.qplayer.ui.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileStorageProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var sdCard: File

    private fun provider(): FileStorageProvider {
        sdCard = tmp.newFolder("6262-3034")
        sdCard.resolve("Music/set").mkdirs()
        sdCard.resolve("Music/set/b.mp3").writeText("")
        sdCard.resolve("Music/set/A.mp3").writeText("")
        sdCard.resolve("Music/set/set.m3u").writeText("A.mp3\nmissing.mp3")
        sdCard.resolve("Music/set/cover.jpg").writeText("")
        sdCard.resolve("Music/empty").mkdirs()
        sdCard.resolve("Music/only-images").mkdirs()
        sdCard.resolve("Music/only-images/x.jpg").writeText("")
        return FileStorageProvider { listOf(StorageRoot(sdCard.absolutePath, "SD card", isRemovable = true)) }
    }

    private fun path(relative: String) = sdCard.resolve(relative).absolutePath

    @Test
    fun `lists supported entries in browser order with their kind`() {
        val items = provider().list(path("Music/set"))!!

        assertEquals(listOf("set.m3u", "A.mp3", "b.mp3"), items.map { it.name })
        assertEquals(
            listOf(BrowserItem.Kind.PLAYLIST, BrowserItem.Kind.TRACK, BrowserItem.Kind.TRACK),
            items.map { it.kind }
        )
        assertEquals(path("Music/set/A.mp3"), items[1].id)
    }

    @Test
    fun `can't enter files, missing or completely empty folders`() {
        val storage = provider()
        assertNull(storage.list(path("Music/set/A.mp3")))
        assertNull(storage.list(path("Music/nope")))
        assertNull(storage.list(path("Music/empty")))
        // Entries, but none supported: entered, shown empty (as before)
        assertEquals(emptyList<BrowserItem>(), storage.list(path("Music/only-images")))
    }

    @Test
    fun `storage, parent, display name and tracks of a folder`() {
        val storage = provider()

        assertEquals("SD card", storage.rootOf(path("Music/set"))?.label)
        assertNull(storage.rootOf("/storage/emulated/0/Music"))
        assertEquals(path("Music"), storage.parentOf(path("Music/set")))
        assertEquals("SD card/Music/set", storage.displayName(path("Music/set")))
        assertEquals(listOf("A.mp3", "b.mp3"), storage.tracksIn(path("Music/set")).map { it.name })
    }

    @Test
    fun `playlist text and track existence`() {
        val storage = provider()

        assertEquals("A.mp3\nmissing.mp3", storage.readText(path("Music/set/set.m3u")))
        assertNull(storage.readText(path("Music/set/nope.m3u")))
        assertTrue(storage.exists(path("Music/set/A.mp3")))
        assertFalse(storage.exists(path("Music/set/missing.mp3")))
        assertFalse(storage.exists(path("Music/set")))
    }
}
