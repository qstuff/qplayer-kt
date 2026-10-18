package org.qstuff.qplayer.datasource.room

import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 4/1/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
/** Playlist/track persistence (the DAOs are suspending; Room runs them off the main thread). */
class RoomDataSource(private val database: QDeqDatabase) {

    suspend fun getAllPlaylists(): List<Playlist> =
        database.playlistDao().getAll()

    suspend fun addPlaylist(playlist: Playlist) =
        database.playlistDao().insertAll(playlist)

    suspend fun removePlaylist(playlist: Playlist) =
        database.playlistDao().delete(playlist.name)

    suspend fun getTracksForPlaylist(playlistName: String): List<Track>? =
        database.trackDao().getTracksForPlaylist(playlistName)

    suspend fun addTracks(tracklist: List<Track>) =
        database.trackDao().insertAll(tracklist)

    suspend fun removeTracksForPlaylist(playlistName: String) =
        database.trackDao().deleteTracksForPlaylist(playlistName)
}
