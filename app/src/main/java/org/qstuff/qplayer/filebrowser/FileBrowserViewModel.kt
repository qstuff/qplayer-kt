package org.qstuff.qplayer.filebrowser

import android.os.Environment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.util.isSupported
import timber.log.Timber
import java.io.File
import java.util.*

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/10/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class FileBrowserViewModel: ViewModel(), KoinComponent {

    companion object {
        const val SD_CARD_HACK_PATH = "/storage/emulated"
    }

    var fileList: MutableLiveData<List<File>> = MutableLiveData()
    var directoryName: MutableLiveData<String> = MutableLiveData()


    private var currentDir: File
    private val preferencesDataSource by inject<PreferencesDataSource>()


    init {
        Timber.d("init()")
        currentDir = File(preferencesDataSource.getLastBrowsedDir())
        browseTo(currentDir)
        Timber.d("init(): ${currentDir.path}")
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("onCleared(): ${currentDir.path}")
        preferencesDataSource.saveLastBrowsedDir(currentDir.path)
    }

    fun navigateUp() {
        Timber.d("navigateUp(): current dir: ${currentDir.absolutePath}")

        if (currentDir.parentFile.absolutePath == SD_CARD_HACK_PATH) {

            Timber.d("navigateUp(): SD_HACK current dir 1: ${currentDir.path}")

            currentDir = File(preferencesDataSource.getRootDir())

            Timber.d("navigateUp(): SD_HACK current dir 2: ${currentDir.path}")

            browseTo(currentDir)

        } else if  (currentDir.absolutePath == preferencesDataSource.getRootDir()

                || currentDir.absolutePath == "/"
                || currentDir.parentFile.absolutePath == "/") {
            return

        } else {
            browseTo(currentDir.parentFile)
        }
    }

    fun onFileItemClicked(file: File) {
        browseTo(file)
    }

    fun saveLastBrowsedDir() {
        preferencesDataSource.saveLastBrowsedDir(currentDir.absolutePath)
    }

    private fun browseTo(dir: File) {
        Timber.d("browseTo(): ${dir.path}")

        var nextDir = dir

        if (nextDir.absolutePath == SD_CARD_HACK_PATH) {
            Timber.d("browseTo(): SD_HACK: ${dir.path}")
            nextDir = File(Environment.getExternalStorageDirectory().path)
        }

        Timber.d("browseTo(): nextDir: $nextDir")

        if (nextDir.isDirectory) {
            Timber.d("browseTo(): is Directory")

            val fileList =  nextDir.listFiles()

            if (!fileList.isNullOrEmpty()) {
                currentDir = nextDir
                filterFileList(fileList.asList())
            } else {

                Timber.w("browseTo(): empty: ${dir.path}")
            }
        } else if (nextDir.isFile) {
            Timber.w("browseTo(): is file: ${dir.path}")
        } else {
            Timber.w("browseTo(): does not exist: ${dir.path}")
        }

        saveLastBrowsedDir()
    }

    private fun filterFileList(files: List<File>) {
        Timber.d("filterFileList(): num: ${files.size}")

        if (files.isNullOrEmpty()) {
            return
        }

        val supportedFiles = arrayListOf<File>()

        Collections.sort(files, FileItemsComparator())

        supportedFiles.addAll(files.filter { it.isSupported() })
        fileList.value = supportedFiles
        directoryName.value = currentDir.absolutePath
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