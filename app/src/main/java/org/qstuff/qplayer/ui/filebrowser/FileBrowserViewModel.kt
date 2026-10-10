package org.qstuff.qplayer.ui.filebrowser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.util.isSupported
import co.touchlab.kermit.Logger
import java.io.File
import java.util.*


private val log = Logger.withTag("FileBrowserViewModel")

/**
 * What the file browser shows. Updated as one object, so the header and the list always change
 * together.
 */
data class FileBrowserState(
    /** The current directory relative to its storage, e.g. "SD card/Music". */
    val directoryName: String = "",
    val files: List<File> = emptyList(),
    /**
     * Non-null while the storage list (the level above all storage roots) is shown instead of a
     * directory — that's where internal storage and the SD card are picked.
     */
    val storageRoots: List<StorageRoot>? = null
)

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 2/10/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class FileBrowserViewModel(
    application: Application
) : AndroidViewModel(application), KoinComponent {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    /** The browsed directory; null while the storage list is shown. */
    private var currentDir: File? = null
    private val preferencesDataSource by inject<PreferencesDataSource>()


    init {
        // Reopen the last browsed directory if its storage is still mounted (an SD card may have
        // been removed meanwhile), else start at the storage list.
        val lastDir = File(preferencesDataSource.getLastBrowsedDir())
        if (rootOf(lastDir, StorageRoots.find(application)) != null) {
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
        val roots = StorageRoots.find(getApplication())

        if (roots.any { it.dir.absolutePath == dir.absolutePath } || rootOf(dir, roots) == null) {
            showStorageRoots(roots)
        } else {
            browseTo(dir.parentFile)
        }
    }

    fun onFileItemClicked(file: File) {
        browseTo(file)
    }

    fun onStorageRootClicked(root: StorageRoot) {
        browseTo(root.dir)
    }

    fun saveLastBrowsedDir() {
        currentDir?.let { preferencesDataSource.saveLastBrowsedDir(it.absolutePath) }
    }

    private fun showStorageRoots(roots: List<StorageRoot> = StorageRoots.find(getApplication())) {
        log.d { "showStorageRoots(): ${roots.map { it.dir }}" }
        currentDir = null
        _state.value = FileBrowserState(storageRoots = roots)
    }

    private fun browseTo(dir: File?) {
        dir ?: return
        log.d { "browseTo(): ${dir.path}" }

        if (dir.isDirectory) {
            val fileList = dir.listFiles()

            if (!fileList.isNullOrEmpty()) {
                currentDir = dir
                filterFileList(dir, fileList.asList())
            } else {
                log.w { "browseTo(): empty: ${dir.path}" }
            }
        } else if (dir.isFile) {
            log.w { "browseTo(): is file: ${dir.path}" }
        } else {
            log.w { "browseTo(): does not exist: ${dir.path}" }
        }
        saveLastBrowsedDir()
    }

    private fun filterFileList(dir: File, files: List<File>) {
        if (files.isEmpty()) {
            return
        }

        val supportedFiles = arrayListOf<File>()

        Collections.sort(files, FileItemsComparator())

        supportedFiles.addAll(files.filter { it.isSupported() })
        _state.value = FileBrowserState(directoryName = displayName(dir), files = supportedFiles)
    }

    /** "<storage label><path below the storage root>", e.g. "SD card/Music/House". */
    private fun displayName(dir: File): String {
        val root = rootOf(dir, StorageRoots.find(getApplication())) ?: return dir.absolutePath
        return root.label + dir.absolutePath.removePrefix(root.dir.absolutePath)
    }

    /** The storage root [dir] lies on, or null if it's on none of the mounted ones. */
    private fun rootOf(dir: File, roots: List<StorageRoot>): StorageRoot? {
        val path = dir.absolutePath
        return roots.firstOrNull {
            val rootPath = it.dir.absolutePath
            path == rootPath || path.startsWith("$rootPath/")
        }
    }

    /**
     * Sorts:
     * - Alphabetically
     * - Directories before Files
     * - M3U lists before audio files
     */
    private class FileItemsComparator : Comparator<File> {

        override fun compare(file1: File, file2: File): Int {

            if (file1.isDirectory && file2.isFile) {
                return -1
            }

            if (file1.isDirectory && file2.isDirectory) {
                return String.CASE_INSENSITIVE_ORDER.compare(file1.name, file2.name)
            }

            return if (file1.isFile && file2.isFile) {

                if (file2.name.endsWith(".m3u") || file1.name.endsWith(".m3u")) {
                    -1
                } else {
                    String.CASE_INSENSITIVE_ORDER.compare(file1.name, file2.name)
                }
            } else 1
        }
    }
}
