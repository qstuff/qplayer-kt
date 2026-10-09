package org.qstuff.qplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.room.RoomDataSource
import timber.log.Timber

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 4/1/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class PlaylistViewModel: ViewModel(), KoinComponent {

    // Plain UI state, exposed as read-only StateFlow. Always a fresh list copy, never the
    // mutable working list.
    private val _playlistList = MutableStateFlow<List<Playlist>>(emptyList())
    val playlistList: StateFlow<List<Playlist>> = _playlistList.asStateFlow()

    private val roomDataSource by inject<RoomDataSource>()

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

    fun saveTracksAsNewPlaylist(tracks: List<Track>, playlistName: String) {

        val playlist = Playlist(0, playlistName, tracks)

        tracks.forEach {
            it.playlistName = playlistName
        }

        viewModelScope.launch {
            roomDataSource.addPlaylist(playlist)
            roomDataSource.addTracks(tracks)
        }

        currentPlaylistList.add(playlist)
        publish()
    }

    fun saveTracksToExistingPlaylist(tracks: List<Track>?, playlistName: String?, overwrite: Boolean) {

        if (tracks.isNullOrEmpty() || playlistName.isNullOrBlank()) {
            return
        }

        viewModelScope.launch {

            val savedTracks = arrayListOf<Track>()
            if (!overwrite) {
                savedTracks.addAll(roomDataSource.getTracksForPlaylist(playlistName) ?: listOf())
            }
            tracks.forEach {
                it.playlistName = playlistName
                savedTracks.add(it)
            }
            roomDataSource.addTracks(savedTracks)
        }
    }

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

        Timber.d("restorePlaylistAt(): ${playlist.trackList}")
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
