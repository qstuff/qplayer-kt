package org.qstuff.qplayer.ui.queue

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Observables — plain UI state, exposed as read-only StateFlow.
    private val _trackList = MutableStateFlow<List<Track>>(emptyList())
    val trackList: StateFlow<List<Track>> = _trackList.asStateFlow()

    private val _onTrackSelectedIndex = MutableStateFlow(-1)
    val onTrackSelectedIndex: StateFlow<Int> = _onTrackSelectedIndex.asStateFlow()

    private val _repeat = MutableStateFlow(TrackRepeatStatus.NONE)
    val repeat: StateFlow<TrackRepeatStatus> = _repeat.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    // onTrackSelected stays LiveData: it's a one-shot selection EVENT observed by PlayerActivity
    // to trigger loadTrack (re-selecting the same track must reload), where LiveData's Activity-
    // lifecycle observation is a good fit.
    var onTrackSelected = MutableLiveData<Track>()

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
        _trackList.value = ArrayList(currentTrackList)
        if (_shuffle.value == true) {
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
        _trackList.value = ArrayList(currentTrackList)
        if (_shuffle.value == true) {
            addIndexToIndexMap(position)
        }
        saveTrackList()
    }

    fun restoreTrackAt(track: Track, position: Int) {
        Timber.d("restoreTrackAt(): pos: $position, selected: ${_onTrackSelectedIndex.value}")

        addTrackAt(track, position)

        if (position <= _onTrackSelectedIndex.value) {
            _onTrackSelectedIndex.value = (_onTrackSelectedIndex.value + 1)
        } else if (_onTrackSelectedIndex.value < 0) {
            _onTrackSelectedIndex.value = position
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
        var newSelectedIndex = _onTrackSelectedIndex.value
        val iterator = currentTrackList.iterator()

        iterator.forEach {
            if (it.uri == track.uri) {
                indexRemoved = index
                iterator.remove()
                if (_shuffle.value == true) {
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
            _trackList.value = ArrayList(currentTrackList)
            _onTrackSelectedIndex.value = newSelectedIndex
            saveTrackList()
        }
    }

    fun addTrackList(tracks: List<Track>) {

        currentTrackList.addAll(tracks)
        _trackList.value = ArrayList(currentTrackList)
        saveTrackList()
    }

    fun addFileList(files: List<File>) {

        files.forEach {
            if (it.isFile) {
                currentTrackList.add(Track(it))
            }
        }
        _trackList.value = ArrayList(currentTrackList)
        if (_shuffle.value == true) {
            addIndexToIndexMap(currentTrackList.size - 1)
        }
        saveTrackList()
    }

    fun replaceTrackList(tracks: List<Track>) {

        currentTrackList.clear()
        currentTrackList.addAll(tracks)
        _trackList.value = ArrayList(currentTrackList)
        _shuffle.value = false
        _repeat.value = TrackRepeatStatus.NONE
        shufflePlayedIndices.clear()

        saveTrackList()
    }

    fun trackListReordered(tracks: List<Track>) {

        currentTrackList.clear()
        currentTrackList.addAll(tracks)
        // FIXME: Maybe this is not enough
        if (_shuffle.value == true) {
            createIndexMap()
        }
        saveTrackList()
    }

    fun clearTrackList() {

        currentTrackList.clear()
        _trackList.value = ArrayList(currentTrackList)
        _onTrackSelectedIndex.value = -1
        onTrackSelected.value = null
        _shuffle.value = false
        _repeat.value = TrackRepeatStatus.NONE
        shufflePlayedIndices.clear()

        saveTrackList()
    }

    fun onTrackSelected(track: Track) {

        track.isAutoplay = preferencesDataSource.isAutostartEnabled()
        _onTrackSelectedIndex.value = currentTrackList.indexOf(track)
        onTrackSelected.value = track
        saveSelectedTrack()
    }

    fun previousTrack(current: Track?, manual: Boolean = false) {

        if (currentTrackList.isNotEmpty()) {
            current?.let {
                var index = 0
                if (currentTrackList.contains(current)) {
                    index = currentTrackList.indexOf(current)

                    // Repeat-one only repeats on track completion; a manual Prev must still
                    // navigate, so treat ONE as NONE here.
                    val effectiveRepeat =
                        if (manual && _repeat.value == TrackRepeatStatus.ONE) TrackRepeatStatus.NONE
                        else _repeat.value
                    when (effectiveRepeat) {
                        TrackRepeatStatus.ALL -> {
                            if (!isSkipBackToStartEnabled) {
                                if (_shuffle.value == true) {
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
                                if (_shuffle.value == true) {
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
                _onTrackSelectedIndex.value = index
            }
            saveSelectedTrack()
        }
    }

    fun nextTrack(
        current: Track?,
        manual: Boolean = false
    ) {
        Timber.d("nextTrack(): current: $current, manual: $manual")

        if (currentTrackList.isNotEmpty()) {
            current?.let {
                var index = 0
                if (currentTrackList.contains(it)) {

                    // Repeat-one only repeats when a track *finishes*; a manual Next must still
                    // advance, so treat ONE as NONE for manual navigation.
                    val effectiveRepeat =
                        if (manual && _repeat.value == TrackRepeatStatus.ONE) TrackRepeatStatus.NONE
                        else _repeat.value

                    when (effectiveRepeat) {
                        TrackRepeatStatus.ONE -> {
                            index = currentTrackList.indexOf(current)
                            current.trackStatus = Track.TrackStatus.PREPARED
                        }
                        TrackRepeatStatus.ALL -> {
                            if (_shuffle.value == true) {
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

                            if (_shuffle.value == true) {
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
                _onTrackSelectedIndex.value = index
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
        _repeat.value = _repeat.value.next()
        preferencesDataSource.saveRepeatMode(_repeat.value.ordinal)
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value

        if (_shuffle.value) {
            createIndexMap()
        }
        preferencesDataSource.saveShuffleMode(_shuffle.value)
    }

    private fun createIndexMap() {

        shufflePlayedIndices.clear()
        repeat(currentTrackList.size) {
            shufflePlayedIndices[it] = false
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

        preferencesDataSource.saveRepeatMode(_repeat.value.ordinal)
        preferencesDataSource.saveShuffleMode(_shuffle.value)
        saveTrackList()
        saveSelectedTrack()
    }

    fun loadStates() {
        _repeat.value = TrackRepeatStatus.entries.toTypedArray()[preferencesDataSource.readRepeatMode()]
        _shuffle.value = false //preferencesDataSource.readShuffleMode()
        if (_shuffle.value == true) {
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
                    // Restoring a track on app start must never auto-play (isAutoplay could be a
                    // stale `true` persisted from an earlier next/prev while autostart was on).
                    it.isAutoplay = false
                    _onTrackSelectedIndex.value = index
                    onTrackSelected.value = it
                }
                index++
            }
        }
    }

    fun readTrackList() {

        val list = preferencesDataSource.readTrackList(PreferencesDataSource.PREF_QUEUE_LIST)
        currentTrackList = list ?: arrayListOf()
        _trackList.value = ArrayList(currentTrackList)
    }
}