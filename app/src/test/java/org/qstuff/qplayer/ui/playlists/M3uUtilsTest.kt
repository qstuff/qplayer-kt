package org.qstuff.qplayer.ui.playlists

import org.junit.Assert.assertEquals
import org.junit.Test
import org.qstuff.qplayer.util.AudioFiles

class M3uUtilsTest {

    @Test
    fun `resolves relative and absolute entries and skips comments`() {
        val existing = setOf("/music/set/a.mp3", "/other/b.mp3")
        val content = """
            #EXTM3U
            #EXTINF:215,Artist - A
            a.mp3

            #EXTINF:180,Artist - B
            /other/b.mp3
            missing.mp3
        """.trimIndent().replace("\n", "\r\n")   // playlists from Windows tools

        val (found, notFound) = M3uUtils.m3UParserGetTracks(content, "/music/set") { it in existing }

        assertEquals(listOf("/music/set/a.mp3", "/other/b.mp3"), found.map { it.uri })
        assertEquals(listOf("a.mp3", "b.mp3"), found.map { it.name })
        assertEquals(listOf("/music/set/missing.mp3"), notFound.map { it.uri })
    }

    @Test
    fun `file name rules`() {
        assertEquals(true, AudioFiles.isSupported("Track.mp3"))
        assertEquals(true, AudioFiles.isSupported("set.m3u"))
        assertEquals(false, AudioFiles.isSupported(".hidden.mp3"))
        assertEquals(false, AudioFiles.isSupported("cover.jpg"))
        assertEquals("Sweet N Candy - Movement.mp3", AudioFiles.nameOf("/storage/6262-3034/x/Sweet N Candy - Movement.mp3"))
    }
}
