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

    init {

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

        val iterator = currentTracks.iterator()
        iterator.forEach {
            if (it.uri == track.uri) {
                iterator.remove()
            }
        }
        trackList.value = currentTracks
    }

    fun addTrackList(tracks: List<Track>) {
        currentTracks.addAll(tracks)
        trackList.value = currentTracks
    }

    fun addFileList(files: List<File>) {
        files.forEach {
            if (it.isFile) {
                currentTracks.add(Track(it))
            }
        }
        trackList.value = currentTracks
    }

    fun replaceTrackList(tracks: List<Track>) {
        currentTracks.clear()
        currentTracks.addAll(tracks)
        trackList.value = currentTracks
    }

    fun trackListReordered(tracks: List<Track>) {
        currentTracks.clear()
        currentTracks.addAll(tracks)
    }

    fun clearTrackList() {
        currentTracks.clear()
        trackList.value = currentTracks
    }

    fun loadSelectedTrack() {
        Timber.d("loadSelectedTrack(): ")

        val track = preferencesDataSource.readSelectedTrack()

        Timber.d("loadSelectedTrack(): track: ${track?.uri} ")

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
        Timber.d("loadTrackList(): ")

        val list = preferencesDataSource.readTrackList(PreferencesDataSource.PREF_QUEUE_LIST)
        if (list == null) {
            currentTracks = arrayListOf()
        } else {
            currentTracks = list
        }
        trackList.value = currentTracks
    }

    fun saveTrackList() {
        Timber.d("saveTrackList():")
        preferencesDataSource.saveTrackList(PreferencesDataSource.PREF_QUEUE_LIST, currentTracks)
    }

    fun saveSelectedTrack() {
        Timber.d("saveSelectedTrack(): ${onTrackSelected.value}")

        if (onTrackSelected.value != null) {
            Timber.d("saveSelectedTrack(): ${onTrackSelected.value}")
            val track = onTrackSelected.value
            track?.trackStatus = Track.TrackStatus.UNDEFINED
            preferencesDataSource.saveSelectedTrackList(onTrackSelected.value!!)
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