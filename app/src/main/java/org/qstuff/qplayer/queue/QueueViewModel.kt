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
    val repeat = MutableLiveData<TrackRepeatStatus>()
    val shuffle = MutableLiveData<Boolean>()

    // Settings
    private var isSkipBackToStartEnabled = true

    private var currentTracks: ArrayList<Track> = arrayListOf()
    private val random = Random()

    private val preferencesDataSource by inject<PreferencesDataSource>()


    init {
        loadStates()
        loadSettings()
    }

    fun addTrack(track: Track) {

        currentTracks.add(track)
        trackList.value = currentTracks
        saveTrackList()
    }

    fun addTrackAt(track: Track, position: Int) {

        currentTracks.add(position, track)
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
        onTrackSelectedIndex.value = -1
        onTrackSelected.value = null
        saveTrackList()
    }

    fun onTrackSelected(track: Track) {

        track.isAutoplay = preferencesDataSource.isAutostartEnabled()
        onTrackSelectedIndex.value = currentTracks.indexOf(track)
        onTrackSelected.value = track
        saveSelectedTrack()
    }

    fun previousTrack(current: Track?) {

        current?.let {
            if (currentTracks.contains(current)) {
                var index = currentTracks.indexOf(current)

                if (!isSkipBackToStartEnabled) {
                    if (shuffle.value == true) {
                        index = random.nextInt(currentTracks.size - 1)
                    } else {
                        if (index > 0) {
                            index -= 1
                        } else if (index == 0) {
                            index = currentTracks.size - 1
                        }
                    }
                }
                val next = currentTracks.get(index)
                next.isAutoplay = preferencesDataSource.isAutostartEnabled()
                onTrackSelected.value = next
                onTrackSelectedIndex.value = index
            }
        }
        saveSelectedTrack()
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
                    else -> {}
                }
                val next = currentTracks.get(index)
                next.isAutoplay = preferencesDataSource.isAutostartEnabled()
                onTrackSelected.value = next
                onTrackSelectedIndex.value = index
            }
        }
        saveSelectedTrack()
    }

    fun onTrackCompleted(track: Track) {
        Timber.d("onTrackCompleted(): ${track.name}, ${preferencesDataSource.isAutoPlayNextTrackEnabled()}")

        if (preferencesDataSource.isAutoPlayNextTrackEnabled()) {
            nextTrack(track)
        }
    }

    fun toggleRepeat() {
        repeat.value = (repeat.value as TrackRepeatStatus).next()
        if (shuffle.value == true) {
            shuffle.value = false
        }
        preferencesDataSource.saveRepeatMode(repeat.value!!.ordinal)
    }

    fun toggleShuffle() {
        shuffle.value = !(shuffle.value ?: true)
        if (repeat.value != TrackRepeatStatus.NONE) {
            repeat.value = TrackRepeatStatus.NONE
        }
        preferencesDataSource.saveShuffleMode(shuffle.value ?: false)
    }

    fun saveStates() {

        preferencesDataSource.saveRepeatMode(repeat.value!!.ordinal)
        preferencesDataSource.saveShuffleMode(shuffle.value ?: false)
        saveTrackList()
        saveSelectedTrack()
    }

    private fun loadStates() {
        repeat.value = TrackRepeatStatus.values()[preferencesDataSource.readRepeatMode()]
        shuffle.value = preferencesDataSource.readShuffleMode()
    }

    private fun loadSettings() {
        isSkipBackToStartEnabled = preferencesDataSource.isSkipBackToStartEnabled()
    }

    private fun saveTrackList() {
        preferencesDataSource.saveTrackList(PreferencesDataSource.PREF_QUEUE_LIST, currentTracks)
    }

    private fun saveSelectedTrack() {

        if (onTrackSelected.value != null) {
            val track = onTrackSelected.value
            track?.trackStatus = Track.TrackStatus.UNDEFINED
            preferencesDataSource.saveSelectedTrackList(track!!)
        }
    }

    fun readSelectedTrack() {

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

    fun readTrackList() {

        val list = preferencesDataSource.readTrackList(PreferencesDataSource.PREF_QUEUE_LIST)
        if (list == null) {
            currentTracks = arrayListOf()
        } else {
            currentTracks = list
        }
        trackList.value = currentTracks
    }
}