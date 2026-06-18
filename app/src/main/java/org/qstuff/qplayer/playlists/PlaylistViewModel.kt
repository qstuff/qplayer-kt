package org.qstuff.qplayer.playlists

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.room.RoomDataSource
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 4/1/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class PlaylistViewModel: ViewModel(), KoinComponent, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    var playlistList = MutableLiveData<List<Playlist>>()

    private val roomDataSource by inject<RoomDataSource>()

    private var currentPlaylistList = arrayListOf<Playlist>()
    private var lastRemovedPlaylistTracks = arrayListOf<Track>()


    fun loadPlaylists() {

        launch {
            withContext(Dispatchers.Default) {
                currentPlaylistList = roomDataSource.getAllPlaylists() as ArrayList<Playlist>
            }

            playlistList.value = ArrayList(currentPlaylistList)
        }
    }

    fun saveTracksAsNewPlaylist(tracks: List<Track>, playlistName: String) {

        val playlist = Playlist(0, playlistName, tracks)

        launch {
            roomDataSource.addPlaylist(playlist)
        }

        tracks.forEach {
            it.playlistName = playlistName
        }

        launch {
            roomDataSource.addTracks(tracks)
        }

        currentPlaylistList.add(playlist)
        playlistList.value = ArrayList(currentPlaylistList)
    }

    fun saveTracksToExistingPlaylist(tracks: List<Track>?, playlistName: String?, overwrite: Boolean) {

        if (tracks.isNullOrEmpty() || playlistName.isNullOrBlank()) {
            return
        }

        launch {

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

    fun getTracksForPlaylist(playlist: Playlist): List<Track> {

        var tracks: List<Track>? = null
        runBlocking {
            tracks = roomDataSource.getTracksForPlaylist(playlist.name)
        }
        return tracks ?: listOf()
    }

    fun removePlaylist(playlist: Playlist) {

        launch {
            lastRemovedPlaylistTracks.addAll(roomDataSource.getTracksForPlaylist(playlist.name) ?: listOf())
            roomDataSource.removeTracksForPlaylist(playlist.name)
            roomDataSource.removePlaylist(playlist)
        }
        currentPlaylistList.remove(playlist)
        playlistList.value = ArrayList(currentPlaylistList)
    }

    fun restorePlaylistAt(playlist: Playlist, position: Int) {

        Timber.d("restorePlaylistAt(): ${playlist.trackList}")
        launch {
            roomDataSource.addPlaylist(playlist)
        }

        launch {
            if (lastRemovedPlaylistTracks.first().playlistName == playlist.name) {
                roomDataSource.addTracks(lastRemovedPlaylistTracks)
            }
        }

        currentPlaylistList.add(position, playlist)
        playlistList.value = ArrayList(currentPlaylistList)

    }

    fun playlistListReordered(playlists: List<Playlist>) {

        currentPlaylistList.clear()
        currentPlaylistList.addAll(playlists)
    }
}