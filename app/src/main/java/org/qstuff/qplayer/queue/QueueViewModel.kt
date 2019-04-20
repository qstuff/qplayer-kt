package org.qstuff.qplayer.queue

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.util.TrackRepeatStatus
import org.qstuff.qplayer.util.next
import timber.log.Timber
import java.io.File
import java.util.Random

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QueueViewModel: ViewModel(), KoinComponent {

    // Observables
    var trackList = MutableLiveData<List<Track>>()
    var onTrackSelectedIndex = MutableLiveData<Int>()
    var onTrackSelected = MutableLiveData<Track>()

    // Track Control
    val repeat = MutableLiveData<TrackRepeatStatus>()
    val shuffle = MutableLiveData<Boolean>()

    private var currentTracks: ArrayList<Track> = arrayListOf()
    private val random = Random()

    private var autoplay = false

    private val preferencesDataSource by inject<PreferencesDataSource>()


    init {
        loadStates()
    }

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
                }
                else if (index == 0) {
                    index = currentTracks.size -1
                }
                onTrackSelected.value = currentTracks.get(index)
                onTrackSelectedIndex.value = index
            }
        }
    }

    fun nextTrack(current: Track?) {

        current?.let {
            if (currentTracks.contains(current)) {
                var index = currentTracks.indexOf(current)

                when (repeat.value) {
                    TrackRepeatStatus.ALL,
                    TrackRepeatStatus.NONE -> {
                        if (shuffle.value == true) {
                            index = random.nextInt(currentTracks.size - 1)
                        } else {
                            if (index < currentTracks.size - 1) {
                                index += 1
                            } else if (index == currentTracks.size - 1) {
                                index = 0
                            }
                        }
                    }
                }
                onTrackSelected.value = currentTracks.get(index)
                onTrackSelectedIndex.value = index
            }
        }
    }

    fun onTrackCompleted(track: Track) {
        Timber.d("onTrackCompleted(): ${track.name}")

        // TODO: if proceedToNextTrack == true
        nextTrack(track)
    }

    fun toggleRepeat() {
        repeat.value = (repeat.value as TrackRepeatStatus).next()
        if (shuffle.value == true) {
            shuffle.value = false
        }
        saveStates()
    }

    fun toggleShuffle() {
        shuffle.value = !(shuffle.value ?: true)
        if (repeat.value != TrackRepeatStatus.NONE) {
            repeat.value = TrackRepeatStatus.NONE
        }
        saveStates()
    }

    fun saveStates() {
        preferencesDataSource.saveRepeatMode(repeat.value!!.ordinal)
        preferencesDataSource.saveShuffleMode(shuffle.value ?: false)
    }

    private fun loadStates() {
        repeat.value = TrackRepeatStatus.values()[preferencesDataSource.readRepeatMode()]
        shuffle.value = preferencesDataSource.readShuffleMode()
    }
}