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
    var isShowClearQueueWarningEnabled = true

    private var currentTrackList: ArrayList<Track> = arrayListOf()
    // Tracks already played in the current shuffle round, keyed by URI so adding, removing or
    // reordering queue entries can't desync it (an index-keyed map did).
    private val shufflePlayedUris = mutableSetOf<String>()

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
        saveTrackList()
    }

    fun replaceTrackList(tracks: List<Track>) {

        currentTrackList.clear()
        currentTrackList.addAll(tracks)
        _trackList.value = ArrayList(currentTrackList)
        _shuffle.value = false
        _repeat.value = TrackRepeatStatus.NONE
        shufflePlayedUris.clear()

        saveTrackList()
    }

    fun trackListReordered(tracks: List<Track>) {

        currentTrackList.clear()
        currentTrackList.addAll(tracks)
        saveTrackList()
    }

    fun clearTrackList() {

        currentTrackList.clear()
        _trackList.value = ArrayList(currentTrackList)
        _onTrackSelectedIndex.value = -1
        onTrackSelected.value = null
        _shuffle.value = false
        _repeat.value = TrackRepeatStatus.NONE
        shufflePlayedUris.clear()

        saveTrackList()
    }

    fun onTrackSelected(track: Track) {

        track.isAutoplay = preferencesDataSource.isAutostartEnabled()
        _onTrackSelectedIndex.value = currentTrackList.indexOf(track)
        onTrackSelected.value = track
        saveSelectedTrack()
    }

    //
    // Navigation
    //
    // Manual Next/Prev always follow plain queue order (wrapping around) — shuffle and repeat only
    // apply to auto-advance when a track finishes, see onTrackCompleted(). A current track that
    // isn't in the queue (e.g. played straight from the file browser) starts at the first entry.
    //

    /** Manual Next: the following queue entry, wrapping from the last to the first. */
    fun nextTrack(current: Track?) {
        if (currentTrackList.isEmpty()) return
        val index = indexInQueue(current)
        selectTrackAt(if (index < 0) 0 else (index + 1) % currentTrackList.size)
    }

    /**
     * Manual Prev: the preceding queue entry, wrapping from the first to the last. (Restarting the
     * current track when it has already played a while is decided by the caller — see
     * PlayerViewModel.restartCurrentTrackIfPlayed().)
     */
    fun previousTrack(current: Track?) {
        if (currentTrackList.isEmpty()) return
        val index = indexInQueue(current)
        val size = currentTrackList.size
        selectTrackAt(if (index < 0) 0 else (index - 1 + size) % size)
    }

    /**
     * Auto-advance after [track] finished playing. Applies repeat and shuffle:
     * - repeat ONE: play the same track again
     * - repeat ALL: next entry, wrapping around (shuffle: random unplayed, new round when all played)
     * - repeat NONE: next entry, stopping after the last (shuffle: stop once all have played)
     *
     * @return true if a following track was selected, false if playback should stop here.
     */
    fun onTrackCompleted(track: Track): Boolean {
        if (!isProceedToNextTrackEnabled || currentTrackList.isEmpty()) return false

        val index = indexInQueue(track)
        val repeat = _repeat.value
        val nextIndex: Int? = when {
            repeat == TrackRepeatStatus.ONE && index >= 0 -> index
            _shuffle.value -> nextShuffleIndex(index, newRoundWhenDone = repeat == TrackRepeatStatus.ALL)
            index + 1 < currentTrackList.size -> index + 1
            repeat == TrackRepeatStatus.ALL -> 0
            else -> null
        }
        Timber.d("onTrackCompleted(): index: $index, repeat: $repeat, shuffle: ${_shuffle.value} -> $nextIndex")

        nextIndex ?: return false
        selectTrackAt(nextIndex)
        return true
    }

    /**
     * Pick a random queue entry not yet played in this shuffle round (never the one that just
     * finished). Once every entry has played, either start a new round or return null (stop).
     */
    private fun nextShuffleIndex(currentIndex: Int, newRoundWhenDone: Boolean): Int? {
        if (currentIndex >= 0) shufflePlayedUris.add(currentTrackList[currentIndex].uri)

        var candidates = currentTrackList.indices.filter {
            it != currentIndex && currentTrackList[it].uri !in shufflePlayedUris
        }
        if (candidates.isEmpty()) {
            if (!newRoundWhenDone) return null
            shufflePlayedUris.clear()
            candidates = currentTrackList.indices.filter { it != currentIndex }
                .ifEmpty { listOf(currentIndex) }   // single-entry queue: replay it
        }
        return candidates[random.nextInt(candidates.size)]
    }

    private fun indexInQueue(track: Track?): Int =
        if (track == null) -1 else currentTrackList.indexOfFirst { it.uri == track.uri }

    private fun selectTrackAt(index: Int) {
        val track = currentTrackList[index]
        track.isAutoplay = preferencesDataSource.isAutostartEnabled()
        onTrackSelected.value = track
        _onTrackSelectedIndex.value = index
        saveSelectedTrack()
    }

    fun toggleRepeat() {
        _repeat.value = _repeat.value.next()
        preferencesDataSource.saveRepeatMode(_repeat.value.ordinal)
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
        shufflePlayedUris.clear()   // switching shuffle on/off starts a fresh round
        preferencesDataSource.saveShuffleMode(_shuffle.value)
    }

    fun saveStates() {

        preferencesDataSource.saveRepeatMode(_repeat.value.ordinal)
        preferencesDataSource.saveShuffleMode(_shuffle.value)
        saveTrackList()
        saveSelectedTrack()
    }

    fun loadStates() {
        _repeat.value = TrackRepeatStatus.entries.toTypedArray()[preferencesDataSource.readRepeatMode()]
        _shuffle.value = preferencesDataSource.readShuffleMode()
    }

    fun loadSettings() {
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