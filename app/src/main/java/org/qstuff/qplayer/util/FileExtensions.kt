package org.qstuff.qplayer.util

import java.io.File

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */

fun File.isM3UList() = this.name.endsWith(".m3u")

fun File.isSupported(): Boolean {
    if (this.isDirectory) {
        return !this.name.startsWith(".")
        //        && this.listFiles().isNotEmpty()
    }
    if (this.isFile) {
        return !this.name.startsWith(".")
                && (this.name.endsWith(".mp3")
                || this.name.endsWith(".mp4")
                || this.name.endsWith(".m4a")
                || this.name.endsWith(".aif")
                || this.name.endsWith(".aac")
                || this.name.endsWith(".wav")
                || this.name.endsWith(".m3u"))
    }
    return false
}

fun File.directoryContainsFiles(): Boolean {
    val files = this.listFiles()

    if (files.size > 0) {
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
    val files = this.listFiles()
    files.forEach {
        if (it.isFile && it.isSupported()) {
            ret.add(it)
        }
    }
    return ret
}