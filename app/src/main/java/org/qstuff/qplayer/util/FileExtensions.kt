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

/** The audio files directly in this folder (no playlists), in [fileBrowserOrder]. */
fun File.listTracksForAddDialog(): List<File> =
    listFiles().orEmpty()
        .filter { it.isFile && it.isSupported() && !it.isM3UList() }
        .sortedWith(fileBrowserOrder)

/**
 * File browser order: folders, then M3U playlists, then audio files — each alphabetically, ignoring
 * case. Also used when adding a whole folder, so the queue gets the order shown in the browser
 * (listFiles() itself has no defined order).
 */
val fileBrowserOrder: Comparator<File> =
    compareBy<File> {
        when {
            it.isDirectory -> 0
            it.isM3UList() -> 1
            else -> 2
        }
    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
