package org.qstuff.qplayer.filebrowser

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getExternalFilesDirs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.util.isSupported
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.collections.ArrayList


/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/10/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
@RequiresApi(Build.VERSION_CODES.R)
class FileBrowserViewModel(
    application: Application
) : AndroidViewModel(application), KoinComponent {

    companion object {
        val SAMSUNG_SD_CARD_HACK_PATH = listOf(
            "/storage/3437-6533",
            "/storage/3865-3532",
            "/storage/6262-3034",
        )

        const val SD_CARD_HACK_PATH = "/storage/emulated"
    }

    var fileList: MutableLiveData<List<File>> = MutableLiveData()
    var directoryName: MutableLiveData<String> = MutableLiveData()


    private var currentDir: File
    private val preferencesDataSource by inject<PreferencesDataSource>()


    init {
        Timber.d("init()")


        val dirs = getExternalFilesDirs(application, "")
        for (currD in dirs) {
            if (currD.absolutePath == getApplication<QDeqApplication>().getExternalFilesDir("")?.absolutePath) {
                Timber.d("XXX INTERNAL: $currD")
            } else {
                Timber.d("XXX EXTERNAL: $currD")
            }
        }

        currentDir = File(preferencesDataSource.getLastBrowsedDir())
        browseTo(currentDir)
        Timber.d("init(): ${currentDir.path}")

        browseTo(currentDir)
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("onCleared(): ${currentDir.path}")
        preferencesDataSource.saveLastBrowsedDir(currentDir.path)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun navigateUp() {
        Timber.d("navigateUp(): current dir: ${currentDir.absolutePath}")

        if (currentDir.parentFile?.absolutePath  == SD_CARD_HACK_PATH) {

            Timber.d("navigateUp(): SD_HACK current dir 1: ${currentDir.path}")

            currentDir = File(preferencesDataSource.getRootDir())

            Timber.d("navigateUp(): SD_HACK current dir 2: ${currentDir.path}")

            browseTo(currentDir)

        } else if  (currentDir.absolutePath == preferencesDataSource.getRootDir()
                || currentDir.absolutePath == "/"
                || currentDir.parentFile?.absolutePath  == "/") {
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

    @RequiresApi(Build.VERSION_CODES.R)
    private fun browseTo(dir: File?) {
        dir?.let {
            Timber.d("xxx browseTo(): ${dir.path}")

            var nextDir = it

            if (nextDir.absolutePath == SD_CARD_HACK_PATH) {
                Timber.d("xxx browseTo(): SD_HACK: ${dir.path}")
                nextDir = Environment.getExternalStorageDirectory()
            }

            val root = Environment.getExternalStorageDirectory()
            Timber.d("xxx browseTo(): root: $root")
            Timber.d("xxx browseTo(): root: ${root.listFiles()?.size}")
            Timber.d("xxx browseTo(): nextDir: $nextDir")

            if (nextDir.isDirectory) {
                Timber.d("xxx browseTo(): is Directory")

                val fileList =  nextDir.listFiles()

                Timber.d("xxx browseTo(): files: ${fileList?.size}")

                if (!fileList.isNullOrEmpty()) {
                    currentDir = nextDir
                    filterFileList(fileList.asList())
                } else {
                    Timber.w("xxx browseTo(): empty: ${dir.path}")
                }
            } else if (nextDir.isFile) {
                Timber.w("xxx browseTo(): is file: ${dir.path}")
            } else {
                Timber.w("xxx browseTo(): does not exist: ${dir.path}")
            }
            saveLastBrowsedDir()
        }
    }

    private fun filterFileList(files: List<File>) {
        Timber.d("XXX filterFileList(): num: ${files.size}")

        if (files.isEmpty()) {
            return
        }

        val supportedFiles = arrayListOf<File>()

        Collections.sort(files, FileItemsComparator())

        supportedFiles.addAll(files.filter { it.isSupported() })
        fileList.value = supportedFiles
        directoryName.value = currentDir.absolutePath

        Timber.d("XXX filterFileList(): supp: ${(fileList.value as ArrayList<File>).size}")
        Timber.d("XXX filterFileList(): dir : ${directoryName.value}")

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