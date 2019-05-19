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
    private var isProceedToNextTrackEnabled = true
    private var isSkipBackToStartEnabled = true
    var isShowClearQueueWarningEnabled = true

    private var currentTrackList: ArrayList<Track> = arrayListOf()
    private var shufflePlayedIndices: HashMap<Int, Boolean> = hashMapOf()

    private val random = Random()

    private val preferencesDataSource by inject<PreferencesDataSource>()


    fun addTrack(track: Track) {

        currentTrackList.add(track)
        trackList.value = currentTrackList
        saveTrackList()
    }

    fun addTrackAt(track: Track, position: Int) {

        currentTrackList.add(position, track)
        trackList.value = currentTrackList
        saveTrackList()
    }

    fun addFile(file: File) {
        if (file.isFile) {
            addTrack(Track(file))
        }
    }

    fun removeTrack(track: Track) {

        val iterator = currentTrackList.iterator()
        iterator.forEach {
            if (it.uri == track.uri) {
                iterator.remove()
            }
        }
        trackList.value = currentTrackList
        saveTrackList()
    }

    fun addTrackList(tracks: List<Track>) {

        currentTrackList.addAll(tracks)
        trackList.value = currentTrackList
        saveTrackList()
    }

    fun addFileList(files: List<File>) {

        files.forEach {
            if (it.isFile) {
                currentTrackList.add(Track(it))
            }
        }
        trackList.value = currentTrackList
        saveTrackList()
    }

    fun replaceTrackList(tracks: List<Track>) {

        currentTrackList.clear()
        currentTrackList.addAll(tracks)
        trackList.value = currentTrackList
        saveTrackList()
    }

    fun trackListReordered(tracks: List<Track>) {

        currentTrackList.clear()
        currentTrackList.addAll(tracks)
        saveTrackList()
    }

    fun clearTrackList() {

        currentTrackList.clear()
        trackList.value = currentTrackList
        onTrackSelectedIndex.value = -1
        onTrackSelected.value = null
        saveTrackList()
    }

    fun onTrackSelected(track: Track) {

        track.isAutoplay = preferencesDataSource.isAutostartEnabled()
        onTrackSelectedIndex.value = currentTrackList.indexOf(track)
        onTrackSelected.value = track
        saveSelectedTrack()
    }

    fun previousTrack(current: Track?) {

        if (currentTrackList.isNotEmpty()) {
            current?.let {
                var index = 0
                if (currentTrackList.contains(current)) {
                    index = currentTrackList.indexOf(current)

                    when (repeat.value) {
                        TrackRepeatStatus.ALL -> {
                            if (!isSkipBackToStartEnabled) {
                                if (shuffle.value == true) {
                                    val unplayedIndices = getYetUnplayedIndices()
                                    if (unplayedIndices.size == 1) {
                                        index = unplayedIndices.get(0)
                                        createIndexMap()
                                    } else if (unplayedIndices.isNotEmpty()) {
                                        index = processShuffleNext(unplayedIndices)
                                    }
                                } else {
                                    index = processPrevious(current)
                                }
                            }
                        }
                        TrackRepeatStatus.NONE -> {
                            if (!isSkipBackToStartEnabled) {
                                if (shuffle.value == true) {
                                    val unplayedIndices = getYetUnplayedIndices()
                                    if (unplayedIndices.size == 1) {
                                        index = unplayedIndices.get(0)
                                        shufflePlayedIndices.put(index, true)
                                    } else if (unplayedIndices.isNotEmpty()) {
                                        index = processShuffleNext(unplayedIndices)
                                    }
                                } else {
                                    index = processPrevious(current)
                                }
                            }
                        }
                        else -> {
                        }
                    }
                }
                val next = currentTrackList.get(index)
                next.isAutoplay = preferencesDataSource.isAutostartEnabled()
                onTrackSelected.value = next
                onTrackSelectedIndex.value = index
            }
            saveSelectedTrack()
        }
    }

    fun nextTrack(current: Track?) {

        if (currentTrackList.isNotEmpty()) {
            current?.let {
                var index = 0
                if (currentTrackList.contains(current)) {

                    when (repeat.value) {
                        TrackRepeatStatus.ALL -> {
                            if (shuffle.value == true) {
                                val unplayedIndices = getYetUnplayedIndices()
                                if (unplayedIndices.size == 1) {
                                    index = unplayedIndices.get(0)
                                    createIndexMap()
                                } else if (unplayedIndices.isNotEmpty()) {
                                    index = processShuffleNext(unplayedIndices)
                                }
                            } else {
                                index = processNext(current)
                            }
                        }
                        TrackRepeatStatus.NONE -> {

                            if (shuffle.value == true) {
                                val unplayedIndices = getYetUnplayedIndices()
                                if (unplayedIndices.size == 1) {
                                    index = unplayedIndices.get(0)
                                    shufflePlayedIndices.put(index, true)
                                } else if (unplayedIndices.isNotEmpty()) {
                                    index = processShuffleNext(unplayedIndices)
                                }
                            } else {
                                index = processNext(current)
                            }
                        }
                        else -> {
                        }
                    }
                }
                val next = currentTrackList.get(index)
                next.isAutoplay = preferencesDataSource.isAutostartEnabled()
                onTrackSelected.value = next
                onTrackSelectedIndex.value = index
            }
            saveSelectedTrack()
        }
    }

    private fun processShuffleNext(unplayedIndices: List<Int>): Int {
        val randomIndex = random.nextInt(unplayedIndices.size - 1)
        val realIndex = unplayedIndices.get(randomIndex)
        shufflePlayedIndices.put(realIndex, true)
        return realIndex
    }

    private fun processNext(current: Track): Int {
        var index = currentTrackList.indexOf(current)
        if (index < currentTrackList.size - 1) {
            index += 1
        } else if (index == currentTrackList.size - 1) {
            index = 0
        }
        return index
    }

    private fun processPrevious(current: Track): Int {
        var index = currentTrackList.indexOf(current)
        if (index > 0) {
            index -= 1
        } else if (index == 0) {
            index = currentTrackList.size - 1
        }
        return index
    }

    fun onTrackCompleted(track: Track) {

        if (isProceedToNextTrackEnabled) {
            nextTrack(track)
        }
    }

    fun toggleRepeat() {
        repeat.value = (repeat.value as TrackRepeatStatus).next()
        preferencesDataSource.saveRepeatMode(repeat.value!!.ordinal)
    }

    fun toggleShuffle() {
        shuffle.value = !(shuffle.value ?: true)

        if (shuffle.value == true) {
            createIndexMap()
        }

        preferencesDataSource.saveShuffleMode(shuffle.value ?: false)
    }

    private fun createIndexMap() {

        shufflePlayedIndices.clear()
        var i = 0
        currentTrackList.forEach {
            shufflePlayedIndices.put(i, false)
            i++
        }
    }

    private fun getYetUnplayedIndices(): List<Int> {

        val unplayed = arrayListOf<Int>()
        shufflePlayedIndices.forEach { (index, played) ->
            if (!played) {
                unplayed.add(index)
            }
        }
        return unplayed
    }

    fun saveStates() {

        preferencesDataSource.saveRepeatMode(repeat.value!!.ordinal)
        preferencesDataSource.saveShuffleMode(shuffle.value ?: false)
        saveTrackList()
        saveSelectedTrack()
    }

    fun loadStates() {
        repeat.value = TrackRepeatStatus.values()[preferencesDataSource.readRepeatMode()]
        shuffle.value = preferencesDataSource.readShuffleMode()
        if (shuffle.value == true) {
            createIndexMap()
        }
    }

    fun loadSettings() {
        isSkipBackToStartEnabled = preferencesDataSource.isSkipBackToStartEnabled()
        isShowClearQueueWarningEnabled = preferencesDataSource.isShowClearQueueWarningEnabled()
        isProceedToNextTrackEnabled = preferencesDataSource.isProceedToNextTrackEnabled()
    }

    private fun saveTrackList() {
        preferencesDataSource.saveTrackList(PreferencesDataSource.PREF_QUEUE_LIST, currentTrackList)
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
            currentTrackList.forEach {
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
            currentTrackList = arrayListOf()
        } else {
            currentTrackList = list
        }
        trackList.value = currentTrackList
    }
}