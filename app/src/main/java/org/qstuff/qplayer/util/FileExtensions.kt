package org.qstuff.qplayer.util

import java.io.File

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */

// java.io.File helpers for the Android file browser; the rules themselves are in AudioFiles.

fun File.isM3UList() = AudioFiles.isM3uPlaylist(name)

fun File.isSupported(): Boolean = when {
    isDirectory -> !AudioFiles.isHidden(name)
    isFile -> AudioFiles.isSupported(name)
    else -> false
}

fun File.directoryContainsSupportedFiles(): Boolean {
    val files = this.listFiles() ?: return false
    if (files.isNotEmpty()) {
        files.forEach {
            if (it.isFile && it.isSupported()) {
                return true
            }
        }
    }

    return false
}

fun File.listTracks(): List<File> {
    val ret = arrayListOf<File>()
    val files = this.listFiles() ?: return ret
    files.forEach {
        if (it.isFile && it.isSupported()) {
            ret.add(it)
        }
    }
    return ret
}

fun File.listTracksForAddDialog(): List<File> {
    val ret = arrayListOf<File>()
    val files = this.listFiles() ?: return ret
    files.forEach {
        if (it.isFile && it.isSupported() && !it.isM3UList()) {
            ret.add(it)
        }
    }
    return ret
}