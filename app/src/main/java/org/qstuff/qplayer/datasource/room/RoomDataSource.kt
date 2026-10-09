package org.qstuff.qplayer.datasource.room

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 4/1/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
/** Playlist/track persistence. The DAOs are blocking, so every call switches to Dispatchers.IO. */
class RoomDataSource: KoinComponent {

    private val database by inject<QDeqDatabase>()

    suspend fun getAllPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        database.playlistDao().getAll()
    }

    suspend fun addPlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        database.playlistDao().insertAll(playlist)
    }

    suspend fun removePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        database.playlistDao().delete(playlist.name)
    }

    suspend fun getTracksForPlaylist(playlistName: String): List<Track>? = withContext(Dispatchers.IO) {
        database.trackDao().getTracksForPlaylist(playlistName)
    }

    suspend fun addTracks(tracklist: List<Track>) = withContext(Dispatchers.IO) {
        database.trackDao().insertAll(tracklist)
    }

    suspend fun removeTracksForPlaylist(playlistName: String) = withContext(Dispatchers.IO) {
        database.trackDao().deleteTracksForPlaylist(playlistName)
    }
}
