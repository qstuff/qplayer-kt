package org.qstuff.qplayer.queue

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import timber.log.Timber
import java.io.File

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QueueViewModel: ViewModel(), KoinComponent {

    var trackList = MutableLiveData<List<Track>>()
    var onTrackSelectedIndex = MutableLiveData<Int>()
    var onTrackSelected = MutableLiveData<Track>()

    private var currentTracks: ArrayList<Track> = arrayListOf()

    private val preferencesDataSource by inject<PreferencesDataSource>()


    fun addTrack(track: Track) {

        currentTracks.add(track)
        trackList.value = currentTracks
        saveTrackList()
    }

    fun addFile(file: File) {
        if (file.isFile) {
            addTrack(Track(file))
        }
    }

    fun removeTrack(track: Track) {

        val iterator = currentTracks.iterator()
        iterator.forEach {
            if (it.uri == track.uri) {
                iterator.remove()
            }
        }
        trackList.value = currentTracks
        saveTrackList()
    }

    fun addTrackList(tracks: List<Track>) {

        currentTracks.addAll(tracks)
        trackList.value = currentTracks
        saveTrackList()
    }

    fun addFileList(files: List<File>) {

        files.forEach {
            if (it.isFile) {
                currentTracks.add(Track(it))
            }
        }
        trackList.value = currentTracks
        saveTrackList()
    }

    fun replaceTrackList(tracks: List<Track>) {

        currentTracks.clear()
        currentTracks.addAll(tracks)
        trackList.value = currentTracks
        saveTrackList()
    }

    fun trackListReordered(tracks: List<Track>) {

        currentTracks.clear()
        currentTracks.addAll(tracks)
        saveTrackList()
    }

    fun clearTrackList() {

        currentTracks.clear()
        trackList.value = currentTracks
        saveTrackList()
    }

    fun loadSelectedTrack() {

        val track = preferencesDataSource.readSelectedTrack()
        track?.also {

            var index = 0
            currentTracks.forEach {
                Timber.d("loadSelectedTrack(): it: ${it.uri} ")
                if (it.uri == track.uri) {
                    onTrackSelectedIndex.value = index
                    onTrackSelected.value = it
                }
                index++
            }
        }
    }

    fun loadTrackList() {

        val list = preferencesDataSource.readTrackList(PreferencesDataSource.PREF_QUEUE_LIST)
        if (list == null) {
            currentTracks = arrayListOf()
        } else {
            currentTracks = list
        }
        trackList.value = currentTracks
        saveTrackList()
    }

    fun saveTrackList() {
        preferencesDataSource.saveTrackList(PreferencesDataSource.PREF_QUEUE_LIST, currentTracks)
    }

    fun saveSelectedTrack() {

        if (onTrackSelected.value != null) {
            val track = onTrackSelected.value
            track?.trackStatus = Track.TrackStatus.UNDEFINED
            preferencesDataSource.saveSelectedTrackList(track!!)
        }
    }

    fun onTrackSelected(track: Track) {

        onTrackSelectedIndex.value = currentTracks.indexOf(track)
        onTrackSelected.value = track
    }

    fun previousTrack(current: Track?) {

        current?.let {
            if (currentTracks.contains(current)) {
                var index = currentTracks.indexOf(current)
                if (index > 0) {
                    index -= 1
                    onTrackSelected.value = currentTracks.get(index)
                }
                else if (index == 0) {
                    index = currentTracks.size -1
                    onTrackSelected.value = currentTracks.get(index )
                }
                onTrackSelectedIndex.value = index
            }
        }
    }

    fun nextTrack(current: Track?) {

        current?.let {
            if (currentTracks.contains(current)) {
                var index = currentTracks.indexOf(current)
                if (index < currentTracks.size -1) {
                    index += 1
                    onTrackSelected.value = currentTracks.get(index)
                }
                else if (index == currentTracks.size -1) {
                    index = 0
                    onTrackSelected.value = currentTracks.get(index)
                }
                onTrackSelectedIndex.value = index
            }
        }
    }
}