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
