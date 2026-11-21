package org.qstuff.qplayer.ui.queue

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val vm = QueueViewModel(PreferencesDataSource(MapSettings()))

    private fun uris() = vm.trackList.value.map { it.uri }

    @Test
    fun `addPath returns the added track, which undo removes again`() {
        vm.addPath("/music/a.mp3")

        val added = vm.addPath("/music/b.mp3")!!
        assertEquals("b.mp3", added.name)
        assertEquals(listOf("/music/a.mp3", "/music/b.mp3"), uris())

        vm.removeTrack(added)       // the snackbar's undo
        assertEquals(listOf("/music/a.mp3"), uris())
    }

    @Test
    fun `addPath returns null for a track that is already queued`() {
        vm.addPath("/music/a.mp3")

        assertNull(vm.addPath("/music/a.mp3"))
        assertEquals(listOf("/music/a.mp3"), uris())
    }

    @Test
    fun `reordering keeps the selected track selected`() {
        listOf("/music/a.mp3", "/music/b.mp3", "/music/c.mp3").forEach { vm.addPath(it) }
        val (a, b, c) = vm.trackList.value
        vm.onTrackSelected(b)
        assertEquals(1, vm.onTrackSelectedIndex.value)

        vm.reorderTracks(listOf(b, c, a))      // b dragged to the top

        assertEquals(listOf("/music/b.mp3", "/music/c.mp3", "/music/a.mp3"), uris())
        assertEquals(0, vm.onTrackSelectedIndex.value)
    }

    @Test
    fun `adding a folder skips tracks already in the queue`() {
        vm.addPath("/music/a.mp3")
        vm.addPaths(listOf("/music/a.mp3", "/music/b.mp3", "/music/b.mp3"))
        assertEquals(listOf("/music/a.mp3", "/music/b.mp3"), uris())
    }

    @Test
    fun `adding a playlist skips tracks already in the queue`() {
        vm.addPath("/music/a.mp3")
        vm.addTrackList(listOf("/music/b.mp3", "/music/a.mp3").map { Track.fromPath(it) })
        assertEquals(listOf("/music/a.mp3", "/music/b.mp3"), uris())
    }

    @Test
    fun `replacing the queue with a playlist keeps each track once`() {
        vm.replaceTrackList(listOf("/music/a.mp3", "/music/b.mp3", "/music/a.mp3").map { Track.fromPath(it) })
        assertEquals(listOf("/music/a.mp3", "/music/b.mp3"), uris())
    }

    @Test
    fun `undo doesn't add a track that was queued again meanwhile`() {
        val a = vm.addPath("/music/a.mp3")!!
        vm.removeTrack(a)
        vm.addPath("/music/a.mp3")
        vm.restoreTrackAt(a, 0)
        assertEquals(listOf("/music/a.mp3"), uris())
    }

    @Test
    fun `a saved queue with a duplicate is loaded and saved once each`() {
        val prefs = PreferencesDataSource(MapSettings())
        val tracks = listOf("/music/a.mp3", "/music/b.mp3", "/music/a.mp3").map { Track.fromPath(it) }
        prefs.saveTrackList(PreferencesDataSource.PREF_QUEUE_LIST, ArrayList(tracks))

        val loaded = QueueViewModel(prefs).apply { readTrackList() }

        assertEquals(listOf("/music/a.mp3", "/music/b.mp3"), loaded.trackList.value.map { it.uri })
        assertEquals(2, prefs.readTrackList(PreferencesDataSource.PREF_QUEUE_LIST)!!.size)
    }
}
