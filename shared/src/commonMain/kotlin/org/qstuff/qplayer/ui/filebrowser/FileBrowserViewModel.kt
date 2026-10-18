package org.qstuff.qplayer.ui.filebrowser

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.ui.playlists.M3uUtils

private val log = Logger.withTag("FileBrowserViewModel")

/**
 * What the file browser shows. Updated as one object, so the header and the list always change
 * together.
 */
data class FileBrowserState(
    /** The current folder relative to its storage, e.g. "SD card/Music". */
    val directoryName: String = "",
    val items: List<BrowserItem> = emptyList(),
    /**
     * Non-null while the storage list (the level above all storage roots) is shown instead of a
     * folder — that's where internal storage and the SD card are picked.
     */
    val storageRoots: List<StorageRoot>? = null
)

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 2/10/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
/** The file browser — platform independent; what can be browsed comes from the [StorageProvider]. */
class FileBrowserViewModel(
    private val storage: StorageProvider,
    private val preferencesDataSource: PreferencesDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    /** The browsed folder; null while the storage list is shown. */
    private var currentDir: String? = null


    init {
        // Reopen the last browsed folder if its storage is still available (an SD card may have
        // been removed meanwhile), else start at the storage list.
        val lastDir = preferencesDataSource.getLastBrowsedDir()
        if (storage.rootOf(lastDir) != null) {
            browseTo(lastDir)
        }
        if (currentDir == null) showStorageRoots()
    }

    override fun onCleared() {
        super.onCleared()
        saveLastBrowsedDir()
    }

    fun navigateUp() {
        val dir = currentDir ?: return
        val root = storage.rootOf(dir)
        val parent = storage.parentOf(dir)

        if (root == null || root.id == dir || parent == null) {
            showStorageRoots()
        } else {
            browseTo(parent)
        }
    }

    fun onItemClicked(item: BrowserItem) {
        if (item.kind == BrowserItem.Kind.FOLDER) browseTo(item.id)
    }

    fun onStorageRootClicked(root: StorageRoot) {
        browseTo(root.id)
    }

    /** The tracks of [folder] (no playlists), for adding the whole folder to the queue. */
    fun tracksIn(folder: BrowserItem): List<BrowserItem> = storage.tracksIn(folder.id)

    /**
     * The tracks of an M3U [playlist]: those that exist and those that don't. Entries are relative
     * to the playlist's folder or absolute.
     */
    fun playlistTracks(playlist: BrowserItem): Pair<List<Track>, List<Track>> {
        val content = storage.readText(playlist.id) ?: return emptyList<Track>() to emptyList()
        return M3uUtils.m3UParserGetTracks(content, storage.parentOf(playlist.id) ?: "", storage::exists)
    }

    fun saveLastBrowsedDir() {
        currentDir?.let { preferencesDataSource.saveLastBrowsedDir(it) }
    }

    private fun showStorageRoots() {
        val roots = storage.roots()
        log.d { "showStorageRoots(): ${roots.map { it.id }}" }
        currentDir = null
        _state.value = FileBrowserState(storageRoots = roots)
    }

    private fun browseTo(dir: String) {
        log.d { "browseTo(): $dir" }

        val items = storage.list(dir)
        if (items != null) {
            currentDir = dir
            _state.value = FileBrowserState(directoryName = storage.displayName(dir), items = items)
        } else {
            log.w { "browseTo(): not a folder or empty: $dir" }
        }
        saveLastBrowsedDir()
    }
}
