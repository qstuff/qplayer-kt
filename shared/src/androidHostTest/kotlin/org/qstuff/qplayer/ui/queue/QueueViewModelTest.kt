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
}
