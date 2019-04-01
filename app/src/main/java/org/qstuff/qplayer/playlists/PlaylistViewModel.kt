package org.qstuff.qplayer.playlists

import android.provider.Contacts
import android.text.Editable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.room.QDeqDatabase
import org.qstuff.qplayer.datasource.room.RoomDataSource
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

    var currentPlaylistList = arrayListOf<Playlist>()


    fun loadPlaylists() {

        launch {
            async {
                currentPlaylistList = roomDataSource.getAllPlaylists() as ArrayList<Playlist>
            }.await()

            playlistList.value = currentPlaylistList
        }
    }

    fun saveTracksAsNewPlaylist(tracks: List<Track>, name: String) {
        val playlist = Playlist(0, name, tracks)
        currentPlaylistList.add(playlist)

        launch {
            roomDataSource.addPlaylist(playlist)
        }

        playlistList.value = currentPlaylistList
    }
}