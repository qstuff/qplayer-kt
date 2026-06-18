package org.qstuff.qplayer.queue

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.util.TrackRepeatStatus
import org.qstuff.qplayer.util.next
import timber.log.Timber
import java.io.File
import java.util.*

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QueueViewModel: ViewModel(), KoinComponent {

    // Observables
    var trackList = MutableLiveData<List<Track>>()
    var onTrackSelectedIndex = MutableLiveData<Int?>(-1)
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

    private var lastRemovedSelectedIndex = -1


    private fun addTrack(track: Track) {
        Timber.d("addTrack(): $track ")

        if (containsTrack(track)) {
            Timber.d("addTrack(): already in queue ")
            return
        }

        currentTrackList.add(track)
        trackList.value = ArrayList(currentTrackList)
        if (shuffle.value == true) {
            addIndexToIndexMap(currentTrackList.size - 1)
        }
        saveTrackList()
    }

    private fun containsTrack(track: Track) : Boolean {
        for (t in currentTrackList) {
            if (t.uri == track.uri) {
                return true
            }
        }
        return false
    }

    private fun addTrackAt(track: Track, position: Int) {

        currentTrackList.add(position, track)
        trackList.value = ArrayList(currentTrackList)
        if (shuffle.value == true) {
            addIndexToIndexMap(position)
        }
        saveTrackList()
    }

    fun restoreTrackAt(track: Track, position: Int) {
        Timber.d("restoreTrackAt(): pos: $position, selected: ${onTrackSelectedIndex.value}")

        addTrackAt(track, position)

        if (position <= onTrackSelectedIndex.value!!) {
            onTrackSelectedIndex.value = (onTrackSelectedIndex.value!! +1)
        } else if (onTrackSelectedIndex.value!! < 0) {
            onTrackSelectedIndex.value = position
        }
    }

    fun addFile(file: File) {
        if (file.isFile) {
            addTrack(Track(file))
        }
    }

    fun removeTrack(track: Track) {

        var index = 0
        var indexRemoved = -1
        var newSelectedIndex = onTrackSelectedIndex.value
        val iterator = currentTrackList.iterator()

        iterator.forEach {
            if (it.uri == track.uri) {
                indexRemoved = index
                iterator.remove()
                if (shuffle.value == true) {
                    removeIndexFromIndexMap(index)
                }
            }
            index++
        }

        if (newSelectedIndex != null) {

            if (indexRemoved < newSelectedIndex) {
                newSelectedIndex--
            } else if (indexRemoved == newSelectedIndex) {
                newSelectedIndex = -1
                lastRemovedSelectedIndex = indexRemoved
            }
            trackList.value = ArrayList(currentTrackList)
            onTrackSelectedIndex.value = newSelectedIndex
            saveTrackList()
        }
    }

    fun addTrackList(tracks: List<Track>) {

        currentTrackList.addAll(tracks)
        trackList.value = ArrayList(currentTrackList)
        saveTrackList()
    }

    fun addFileList(files: List<File>) {

        files.forEach {
            if (it.isFile) {
                currentTrackList.add(Track(it))
            }
        }
        trackList.value = ArrayList(currentTrackList)
        if (shuffle.value == true) {
            addIndexToIndexMap(currentTrackList.size - 1)
        }
        saveTrackList()
    }

    fun replaceTrackList(tracks: List<Track>) {

        currentTrackList.clear()
        currentTrackList.addAll(tracks)
        trackList.value = ArrayList(currentTrackList)
        shuffle.value = false
        repeat.value = TrackRepeatStatus.NONE
        shufflePlayedIndices.clear()

        saveTrackList()
    }

    fun trackListReordered(tracks: List<Track>) {

        currentTrackList.clear()
        currentTrackList.addAll(tracks)
        // FIXME: Maybe this is not enough
        if (shuffle.value == true) {
            createIndexMap()
        }
        saveTrackList()
    }

    fun clearTrackList() {

        currentTrackList.clear()
        trackList.value = ArrayList(currentTrackList)
        onTrackSelectedIndex.value = -1
        onTrackSelected.value = null
        shuffle.value = false
        repeat.value = TrackRepeatStatus.NONE
        shufflePlayedIndices.clear()

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
                                        index = unplayedIndices[0]
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
                                        index = unplayedIndices[0]
                                        shufflePlayedIndices[index] = true
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
                val next = currentTrackList[index]
                next.isAutoplay = preferencesDataSource.isAutostartEnabled()
                onTrackSelected.value = next
                onTrackSelectedIndex.value = index
            }
            saveSelectedTrack()
        }
    }

    fun nextTrack(current: Track?) {
        Timber.d("nextTrack(): current: $current")

        if (currentTrackList.isNotEmpty()) {
            current?.let {
                var index = 0
                if (currentTrackList.contains(it)) {

                    when (repeat.value) {
                        TrackRepeatStatus.ONE -> {
                            index = currentTrackList.indexOf(current)
                            current.trackStatus = Track.TrackStatus.PREPARED
                        }
                        TrackRepeatStatus.ALL -> {
                            if (shuffle.value == true) {
                                val unplayedIndices = getYetUnplayedIndices()
                                if (unplayedIndices.size == 1) {
                                    index = unplayedIndices[0]
                                    createIndexMap()
                                } else if (unplayedIndices.isNotEmpty()) {
                                    index = processShuffleNext(unplayedIndices)
                                }
                            } else {
                                index = processNext(it)
                            }
                        }
                        TrackRepeatStatus.NONE -> {

                            if (shuffle.value == true) {
                                val unplayedIndices = getYetUnplayedIndices()
                                if (unplayedIndices.size == 1) {
                                    index = unplayedIndices[0]
                                    shufflePlayedIndices[index] = true
                                } else if (unplayedIndices.isNotEmpty()) {
                                    index = processShuffleNext(unplayedIndices)
                                }
                            } else {
                                index = processNext(it)
                            }
                        }
                        else -> {
                        }
                    }
                }
                val next = currentTrackList[index]
                next.isAutoplay = preferencesDataSource.isAutostartEnabled()
                onTrackSelected.value = next
                onTrackSelectedIndex.value = index
            }
            saveSelectedTrack()
        }
    }

    private fun processShuffleNext(unplayedIndices: List<Int>): Int {
        Timber.d("processShuffleNext(): ")

        val randomIndex = random.nextInt(unplayedIndices.size - 1)
        val realIndex = unplayedIndices[randomIndex]
        shufflePlayedIndices[realIndex] = true
        Timber.d("processShuffleNext(): realIndex: $realIndex")
        return realIndex
    }

    private fun processNext(current: Track): Int {
        Timber.d("processNext(): current: $current")

        var index = currentTrackList.indexOf(current)
        if (index < currentTrackList.size - 1) {
            index += 1
        } else if (index == currentTrackList.size - 1) {
            index = 0
        }
        Timber.d("processNext(): index: $index")
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
        repeat(currentTrackList.size) {
            shufflePlayedIndices.put(it, false)
        }
    }

    private fun addIndexToIndexMap(index: Int) {
        shufflePlayedIndices[index] = false
    }

    private fun removeIndexFromIndexMap(index: Int) {
        shufflePlayedIndices.remove(index)
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
        currentTrackList = if (list == null) {
            arrayListOf()
        } else {
            list
        }
        trackList.value = ArrayList(currentTrackList)
    }
}