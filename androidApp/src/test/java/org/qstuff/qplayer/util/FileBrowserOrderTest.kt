package org.qstuff.qplayer.util

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileBrowserOrderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun createFolder() = tmp.newFolder("set").apply {
        listOf("Speedy J 03 Bla.mp3", "speedy j 01 Bla.mp3", "Speedy J 04 Bla.mp3", "Speedy J 02 Bla.mp3",
            "b-side.m3u", "A-side.m3u", "cover.jpg", ".hidden.mp3")
            .forEach { resolve(it).writeText("") }
        listOf("Zeta", "alpha").forEach { resolve(it).mkdir() }
    }

    @Test
    fun `browser order - folders, then playlists, then tracks, alphabetically ignoring case`() {
        val sorted = createFolder().listFiles()!!.filter { it.isSupported() }.sortedWith(fileBrowserOrder)

        assertEquals(
            listOf("alpha", "Zeta", "A-side.m3u", "b-side.m3u",
                "speedy j 01 Bla.mp3", "Speedy J 02 Bla.mp3", "Speedy J 03 Bla.mp3", "Speedy J 04 Bla.mp3"),
            sorted.map { it.name }
        )
    }

    @Test
    fun `adding a folder queues its tracks in browser order`() {
        assertEquals(
            listOf("speedy j 01 Bla.mp3", "Speedy J 02 Bla.mp3", "Speedy J 03 Bla.mp3", "Speedy J 04 Bla.mp3"),
            createFolder().listTracksForAddDialog().map { it.name }
        )
    }
}
