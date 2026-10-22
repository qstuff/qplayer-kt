package org.qstuff.qplayer.ui.filebrowser

import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource

class FileBrowserViewModelTest {

    /** In-memory storages: folder id → entries; ids are "/"-paths like on Android. */
    private class FakeStorage(
        private val roots: List<StorageRoot>,
        private val folders: Map<String, List<BrowserItem>>,
        private val texts: Map<String, String> = emptyMap(),
        private val existing: Set<String> = emptySet()
    ) : StorageProvider {
        override fun roots() = roots
        override fun rootOf(id: String) = roots.firstOrNull { id == it.id || id.startsWith("${it.id}/") }
        override fun list(folderId: String) = folders[folderId]
        override fun parentOf(id: String) = id.substringBeforeLast('/', "").ifEmpty { null }
        override fun tracksIn(folderId: String) = folders[folderId].orEmpty().filter { it.kind == BrowserItem.Kind.TRACK }
        override fun readText(id: String) = texts[id]
        override fun exists(id: String) = id in existing
        override fun displayName(folderId: String) = rootOf(folderId)!!.label + folderId.removePrefix(rootOf(folderId)!!.id)
    }

    private val internal = StorageRoot("/storage/emulated/0", "Internal storage", isRemovable = false)
    private val sdCard = StorageRoot("/storage/6262-3034", "SD card", isRemovable = true)

    private fun folder(id: String) = BrowserItem(id, id.substringAfterLast('/'), BrowserItem.Kind.FOLDER)
    private fun track(id: String) = BrowserItem(id, id.substringAfterLast('/'), BrowserItem.Kind.TRACK)
    private fun playlist(id: String) = BrowserItem(id, id.substringAfterLast('/'), BrowserItem.Kind.PLAYLIST)

    private val storage = FakeStorage(
        roots = listOf(internal, sdCard),
        folders = mapOf(
            "/storage/6262-3034" to listOf(folder("/storage/6262-3034/Music")),
            "/storage/6262-3034/Music" to listOf(
                folder("/storage/6262-3034/Music/set"),
                playlist("/storage/6262-3034/Music/set.m3u"),
                track("/storage/6262-3034/Music/a.mp3")
            ),
            "/storage/6262-3034/Music/set" to listOf(track("/storage/6262-3034/Music/set/b.mp3"))
        ),
        texts = mapOf("/storage/6262-3034/Music/set.m3u" to "#EXTM3U\na.mp3\nset/b.mp3\nmissing.mp3"),
        existing = setOf("/storage/6262-3034/Music/a.mp3", "/storage/6262-3034/Music/set/b.mp3")
    )

    private fun viewModel(lastBrowsed: String? = null): Pair<FileBrowserViewModel, PreferencesDataSource> {
        val prefs = PreferencesDataSource(MapSettings())
        lastBrowsed?.let { prefs.saveLastBrowsedDir(it) }
        return FileBrowserViewModel(storage, prefs) to prefs
    }

    @Test
    fun `starts in the last browsed folder`() {
        val (vm, _) = viewModel(lastBrowsed = "/storage/6262-3034/Music")

        assertEquals("SD card/Music", vm.state.value.directoryName)
        assertEquals(listOf("set", "set.m3u", "a.mp3"), vm.state.value.items.map { it.name })
        assertNull(vm.state.value.storageRoots)
    }

    @Test
    fun `starts at the storage list if the last folder's storage is gone`() {
        val (vm, _) = viewModel(lastBrowsed = "/storage/1111-2222/Music")

        assertEquals(listOf(internal, sdCard), vm.state.value.storageRoots)
    }

    @Test
    fun `navigating down and up, from a storage root up to the storage list`() {
        val (vm, prefs) = viewModel(lastBrowsed = "/storage/6262-3034/Music")

        vm.onItemClicked(folder("/storage/6262-3034/Music/set"))
        assertEquals("SD card/Music/set", vm.state.value.directoryName)
        assertEquals("/storage/6262-3034/Music/set", prefs.getLastBrowsedDir())

        vm.navigateUp()
        vm.navigateUp()
        assertEquals("SD card", vm.state.value.directoryName)

        vm.navigateUp()
        assertEquals(listOf(internal, sdCard), vm.state.value.storageRoots)

        vm.onStorageRootClicked(sdCard)
        assertEquals("SD card", vm.state.value.directoryName)
    }

    @Test
    fun `a folder that can't be listed is not entered`() {
        val (vm, _) = viewModel(lastBrowsed = "/storage/6262-3034/Music")

        vm.onItemClicked(folder("/storage/6262-3034/Music/empty"))

        assertEquals("SD card/Music", vm.state.value.directoryName)
    }

    @Test
    fun `refresh lists the shown folder again`() {
        val folders = mutableMapOf("/storage/6262-3034/Music" to listOf(track("/storage/6262-3034/Music/a.mp3")))
        val prefs = PreferencesDataSource(MapSettings()).apply { saveLastBrowsedDir("/storage/6262-3034/Music") }
        val vm = FileBrowserViewModel(FakeStorage(listOf(internal, sdCard), folders), prefs)

        folders["/storage/6262-3034/Music"] = listOf(track("/storage/6262-3034/Music/a.mp3"), track("/storage/6262-3034/Music/b.mp3"))
        vm.refresh()

        assertEquals(listOf("a.mp3", "b.mp3"), vm.state.value.items.map { it.name })
    }

    @Test
    fun `openFolder opens folders on a storage only`() {
        val (vm, prefs) = viewModel()

        vm.openFolder("/storage/6262-3034/Music/set")
        assertEquals("SD card/Music/set", vm.state.value.directoryName)
        assertEquals("/storage/6262-3034/Music/set", prefs.getLastBrowsedDir())

        vm.openFolder("/somewhere/else")
        assertEquals("SD card/Music/set", vm.state.value.directoryName)
    }

    @Test
    fun `playlist tracks resolved relative to the playlist's folder`() {
        val (vm, _) = viewModel()

        val (found, notFound) = vm.playlistTracks(playlist("/storage/6262-3034/Music/set.m3u"))

        assertEquals(listOf("/storage/6262-3034/Music/a.mp3", "/storage/6262-3034/Music/set/b.mp3"), found.map { it.uri })
        assertEquals(listOf("missing.mp3"), notFound.map { it.name })
    }
}
