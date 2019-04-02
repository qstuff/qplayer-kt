package org.qstuff.qplayer.datasource.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import kotlin.coroutines.CoroutineContext

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 4/1/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class RoomDataSource: KoinComponent, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private val database by inject<QDeqDatabase>()

    suspend fun getAllPlaylists(): List<Playlist> {
        return async {
            database.playlistDao().getAll()
        }.await()
    }

    suspend fun addPlaylist(playlist: Playlist) {
        return async {
            database.playlistDao().insertAll(playlist)
        }.await()
    }

    suspend fun removePlaylist(playlist: Playlist) {
        return async {
            database.playlistDao().delete(playlist)
        }.await()
    }

    suspend fun getTracksForPlaylist(playlistName: String): List<Track>? {
        return async {
            database.trackDao().getTracksForPlaylist(playlistName)
        }.await()
    }

    suspend fun addTracks(tracklist: List<Track>) {
        return async {
            database.trackDao().insertAll(tracklist)
        }.await()
    }
}