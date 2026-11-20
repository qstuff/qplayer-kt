package org.qstuff.qplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.qstuff.qplayer.datasource.model.Track

class PlaybackCoordinatorTest {

    private val queue = listOf("/a.mp3", "/b.mp3", "/c.mp3").map { Track.fromPath(it) }

    @Test
    fun `the following track is the next queue entry, wrapping around`() {
        assertEquals("/b.mp3", followingTrack(queue, "/a.mp3")?.uri)
        assertEquals("/a.mp3", followingTrack(queue, "/c.mp3")?.uri)
    }

    @Test
    fun `no following track outside the queue or in a one-track queue`() {
        assertNull(followingTrack(queue, "/x.mp3"))
        assertNull(followingTrack(queue, null))
        assertNull(followingTrack(queue.take(1), "/a.mp3"))
    }
}
