package org.qstuff.qplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.room.PlaylistStore
import co.touchlab.kermit.Logger

private val log = Logger.withTag("PlaylistViewModel")

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 4/1/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class PlaylistViewModel(
    private val roomDataSource: PlaylistStore
) : ViewModel() {

    // Plain UI state, exposed as read-only StateFlow. Always a fresh list copy, never the
    // mutable working list.
    private val _playlistList = MutableStateFlow<List<Playlist>>(emptyList())
    val playlistList: StateFlow<List<Playlist>> = _playlistList.asStateFlow()


    // The playlist opened in the Playlists tab (null: the list of playlists) and its tracks.
    private val _openedPlaylist = MutableStateFlow<Playlist?>(null)
    val openedPlaylist: StateFlow<Playlist?> = _openedPlaylist.asStateFlow()
    private val _openedTracks = MutableStateFlow<List<PlaylistEntry>>(emptyList())
    val openedTracks: StateFlow<List<PlaylistEntry>> = _openedTracks.asStateFlow()
    private var nextEntryKey = 0L

    private val currentPlaylistList = arrayListOf<Playlist>()
    // Tracks of the last removed playlist, for Undo.
    private val lastRemovedPlaylistTracks = arrayListOf<Track>()


    fun loadPlaylists() {
        viewModelScope.launch {
            val playlists = roomDataSource.getAllPlaylists()
            currentPlaylistList.clear()
            currentPlaylistList.addAll(playlists)
            publish()
        }
    }

    /**
     * Saves copies of [tracks] as a new playlist [playlistName]. It appears in the list once the
     * database has assigned its id (the list is keyed by it — two new ones with id 0 collided).
     */
    fun saveTracksAsNewPlaylist(tracks: List<Track>, playlistName: String) {
        val copies = tracks.copiesFor(playlistName)
        viewModelScope.launch {
            val playlist = Playlist(0, playlistName, copies)
            playlist.playlist_id = roomDataSource.addPlaylist(playlist).toInt()
            roomDataSource.addTracks(copies)
            currentPlaylistList.add(playlist)
            publish()
        }
    }

    /** Saves copies of [tracks] into the playlist [playlistName]: appended, or replacing its tracks. */
    fun saveTracksToExistingPlaylist(tracks: List<Track>?, playlistName: String?, overwrite: Boolean) {

        if (tracks.isNullOrEmpty() || playlistName.isNullOrBlank()) {
            return
        }

        val copies = tracks.copiesFor(playlistName)
        viewModelScope.launch {
            if (overwrite) roomDataSource.removeTracksForPlaylist(playlistName)
            roomDataSource.addTracks(copies)
            if (_openedPlaylist.value?.name == playlistName) loadOpenedTracks(playlistName)
        }
    }

    /** Show [playlist]'s tracks in the Playlists tab. */
    fun openPlaylist(playlist: Playlist) {
        _openedPlaylist.value = playlist
        _openedTracks.value = emptyList()
        viewModelScope.launch { loadOpenedTracks(playlist.name) }
    }

    /** Back to the list of playlists. */
    fun closePlaylist() {
        _openedPlaylist.value = null
        _openedTracks.value = emptyList()
    }

    /** Remove [entry]'s track from the opened playlist (undo: [restoreTrack]). */
    fun removeTrack(entry: PlaylistEntry) {
        _openedTracks.value = _openedTracks.value.filterNot { it.key == entry.key }
        viewModelScope.launch { roomDataSource.removeTracks(listOf(entry.track)) }
    }

    /**
     * Put a removed [entry] back at [index]. Its track keeps its row id, and the tracks are listed
     * in id order, so it's back in its old place after a reload too.
     */
    fun restoreTrack(entry: PlaylistEntry, index: Int) {
        viewModelScope.launch { roomDataSource.addTracks(listOf(entry.track)) }
        if (_openedPlaylist.value?.name == entry.track.playlistName) {
            _openedTracks.value = _openedTracks.value.toMutableList().apply {
                add(index.coerceIn(0, size), entry)
            }
        }
    }

    /**
     * The opened playlist in a new order (the same entries, moved by drag). The tracks are listed
     * by row id, so they get their existing ids in the new order — rewritten in place, no new rows.
     */
    fun reorderTracks(entries: List<PlaylistEntry>) {
        val ids = entries.map { it.track.track_id }.sorted()
        val reordered = entries.mapIndexed { i, entry -> entry.copy(track = entry.track.copy(track_id = ids[i])) }
        _openedTracks.value = reordered
        viewModelScope.launch { roomDataSource.addTracks(reordered.map { it.track }) }
    }

    private suspend fun loadOpenedTracks(playlistName: String) {
        val tracks = roomDataSource.getTracksForPlaylist(playlistName) ?: listOf()
        if (_openedPlaylist.value?.name == playlistName) {
            _openedTracks.value = tracks.map { PlaylistEntry(nextEntryKey++, it) }
        }
    }

    /**
     * New rows for [playlistName]. Never the given objects themselves: tracks loaded from another
     * playlist keep their row id, and saving them would *move* those rows (insert = replace by id)
     * — and the queue's tracks would get the playlist name as a side effect.
     */
    private fun List<Track>.copiesFor(playlistName: String) =
        map { it.copy(track_id = 0, playlistName = playlistName) }

    suspend fun getTracksForPlaylist(playlist: Playlist): List<Track> =
        roomDataSource.getTracksForPlaylist(playlist.name) ?: listOf()

    fun removePlaylist(playlist: Playlist) {

        viewModelScope.launch {
            // Keep only this playlist's tracks for Undo.
            val tracks = roomDataSource.getTracksForPlaylist(playlist.name) ?: listOf()
            lastRemovedPlaylistTracks.clear()
            lastRemovedPlaylistTracks.addAll(tracks)
            roomDataSource.removeTracksForPlaylist(playlist.name)
            roomDataSource.removePlaylist(playlist)
        }
        currentPlaylistList.remove(playlist)
        publish()
    }

    fun restorePlaylistAt(playlist: Playlist, position: Int) {

        log.d { "restorePlaylistAt(): ${playlist.trackList}" }
        viewModelScope.launch {
            roomDataSource.addPlaylist(playlist)
            // An empty playlist has no tracks to restore.
            if (lastRemovedPlaylistTracks.firstOrNull()?.playlistName == playlist.name) {
                roomDataSource.addTracks(ArrayList(lastRemovedPlaylistTracks))
            }
        }

        currentPlaylistList.add(position.coerceIn(0, currentPlaylistList.size), playlist)
        publish()
    }

    fun playlistListReordered(playlists: List<Playlist>) {

        currentPlaylistList.clear()
        currentPlaylistList.addAll(playlists)
    }

    private fun publish() {
        _playlistList.value = ArrayList(currentPlaylistList)
    }
}

/**
 * A track of the opened playlist. [key] identifies it in the list and stays the same when the
 * track is moved — its row id doesn't (see [PlaylistViewModel.reorderTracks]).
 */
data class PlaylistEntry(val key: Long, val track: Track)
