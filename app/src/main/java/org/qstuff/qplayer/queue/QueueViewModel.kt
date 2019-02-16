package org.qstuff.qplayer.queue

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import java.io.File

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QueueViewModel: ViewModel(), KoinComponent {

    var trackList: MutableLiveData<List<Track>> = MutableLiveData()
    var onTrackSelectedIndex: MutableLiveData<Int> = MutableLiveData()

    private var currentTracks: ArrayList<Track> = arrayListOf()

    private val preferencesDataSource by inject<PreferencesDataSource>()

    init {
        loadTrackList()
    }

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
        currentTracks.clear()
        currentTracks.addAll(tracks)
        trackList.value = currentTracks
    }

    fun clearTrackList() {
        currentTracks.clear()
        trackList.value = currentTracks
    }

    fun loadTrackList() {
        val list = preferencesDataSource.readTrackList(PreferencesDataSource.PREF_QUEUE_LIST)
        if (list == null) {
            currentTracks = arrayListOf()
        } else {
            currentTracks = list
        }
        trackList.value = currentTracks
    }

    fun saveTrackList() {
        preferencesDataSource.saveTrackList(PreferencesDataSource.PREF_QUEUE_LIST, currentTracks)
    }

    fun onTrackSelectedIndex(index: Int) {
        onTrackSelectedIndex.value = index
    }
}