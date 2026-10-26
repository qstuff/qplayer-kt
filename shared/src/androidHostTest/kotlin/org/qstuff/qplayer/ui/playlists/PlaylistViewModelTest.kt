package org.qstuff.qplayer.ui.playlists

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.room.PlaylistStore

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    /** Behaves like the Room tables: auto-generated ids, insert = replace by id. */
    private class InMemoryStore : PlaylistStore {
        val playlists = linkedMapOf<Int, Playlist>()
        val tracks = linkedMapOf<Int, Track>()
        private var nextPlaylistId = 1
        private var nextTrackId = 1

        override suspend fun getAllPlaylists() = playlists.values.toList()
        override suspend fun addPlaylist(playlist: Playlist): Long {
            val id = if (playlist.playlist_id == 0) nextPlaylistId++ else playlist.playlist_id
            playlists[id] = playlist.copy(playlist_id = id)
            return id.toLong()
        }
        override suspend fun removePlaylist(playlist: Playlist) { playlists.remove(playlist.playlist_id) }
        override suspend fun getTracksForPlaylist(playlistName: String) =
            tracks.values.filter { it.playlistName == playlistName }
        override suspend fun addTracks(tracklist: List<Track>) {
            tracklist.forEach {
                val id = if (it.track_id == 0) nextTrackId++ else it.track_id
                tracks[id] = it.copy(track_id = id)
            }
        }
        override suspend fun removeTracksForPlaylist(playlistName: String) {
            tracks.values.removeAll { it.playlistName == playlistName }
        }

        fun uris(playlistName: String) = tracks.values.filter { it.playlistName == playlistName }.map { it.uri }
    }

    private val store = InMemoryStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun track(path: String) = Track.fromPath(path)

    @Test
    fun `new playlists get their own ids`() {
        val vm = PlaylistViewModel(store)

        vm.saveTracksAsNewPlaylist(listOf(track("/m/a.mp3")), "One")
        vm.saveTracksAsNewPlaylist(listOf(track("/m/b.mp3")), "Two")

        val ids = vm.playlistList.value.map { it.playlist_id }
        assertEquals(2, ids.size)
        assertNotEquals(0, ids[0])
        assertNotEquals(ids[0], ids[1])
    }

    @Test
    fun `overwrite replaces the playlist's tracks, append adds to them`() = runTest {
        val vm = PlaylistViewModel(store)
        vm.saveTracksAsNewPlaylist(listOf(track("/m/a.mp3"), track("/m/b.mp3")), "Set")

        vm.saveTracksToExistingPlaylist(listOf(track("/m/c.mp3")), "Set", overwrite = false)
        assertEquals(listOf("/m/a.mp3", "/m/b.mp3", "/m/c.mp3"), store.uris("Set"))

        vm.saveTracksToExistingPlaylist(listOf(track("/m/d.mp3")), "Set", overwrite = true)
        assertEquals(listOf("/m/d.mp3"), store.uris("Set"))
    }

    @Test
    fun `saving tracks loaded from another playlist copies them`() = runTest {
        val vm = PlaylistViewModel(store)
        vm.saveTracksAsNewPlaylist(listOf(track("/m/a.mp3"), track("/m/b.mp3")), "A")
        // Loaded into the queue: the tracks carry their row ids from playlist A.
        val queue = vm.getTracksForPlaylist(vm.playlistList.value.single())

        vm.saveTracksAsNewPlaylist(queue, "B")
        vm.saveTracksToExistingPlaylist(queue, "B", overwrite = false)

        assertEquals(listOf("/m/a.mp3", "/m/b.mp3"), store.uris("A"))
        assertEquals(listOf("/m/a.mp3", "/m/b.mp3", "/m/a.mp3", "/m/b.mp3"), store.uris("B"))
        assertEquals(listOf("A", "A"), queue.map { it.playlistName })     // queue tracks untouched
    }
}
