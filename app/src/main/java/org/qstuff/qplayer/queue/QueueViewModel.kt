package org.qstuff.qplayer.queue

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.koin.standalone.KoinComponent
import org.qstuff.qplayer.datasource.model.Track
import timber.log.Timber
import java.io.File

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QueueViewModel: ViewModel(), KoinComponent {

    var trackList: MutableLiveData<List<Track>> = MutableLiveData()
    var onTrackSelectedIndex: MutableLiveData<Int> = MutableLiveData()

    private var currentTracks = arrayListOf<Track>()


    fun addTrack(track: Track) {
        currentTracks.add(track)
        trackList.value = currentTracks
    }

    fun addFile(file: File) {
        if (file.isFile) {
            addTrack(Track(file))
        }
    }

    fun removeTrack(track: Track) {
        currentTracks.remove(track)
        trackList.value = currentTracks
    }

    fun addTrackList(tracks: List<Track>) {
        currentTracks.addAll(tracks)
        trackList.value = currentTracks
    }

    fun addFileList(files: List<File>) {
        files.forEach {
            if (it.isFile) {
                addTrack(Track(it))
            }
        }
    }

    fun replaceTrackList(tracks: List<Track>) {
        currentTracks = arrayListOf()
        currentTracks.addAll(tracks)
        trackList.value = currentTracks
    }

    fun clearTrackList() {
        currentTracks.clear()
        trackList.value = currentTracks
    }

    fun saveTrackList() {
        // TODO: internal for resume
    }

    fun onTrackSelectedIndex(index: Int) {
        onTrackSelectedIndex.value = index
    }
}