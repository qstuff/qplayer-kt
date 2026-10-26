package org.qstuff.qplayer.datasource.room

import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 4/1/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
/** Playlist/track persistence (the DAOs are suspending; Room runs them off the main thread). */
class RoomDataSource(private val database: QDeqDatabase) : PlaylistStore {

    override suspend fun getAllPlaylists(): List<Playlist> =
        database.playlistDao().getAll()

    override suspend fun addPlaylist(playlist: Playlist): Long =
        database.playlistDao().insertAll(playlist)

    override suspend fun removePlaylist(playlist: Playlist) =
        database.playlistDao().delete(playlist.name)

    override suspend fun getTracksForPlaylist(playlistName: String): List<Track>? =
        database.trackDao().getTracksForPlaylist(playlistName)

    override suspend fun addTracks(tracklist: List<Track>) =
        database.trackDao().insertAll(tracklist)

    override suspend fun removeTracks(tracks: List<Track>) =
        database.trackDao().delete(tracks)

    override suspend fun removeTracksForPlaylist(playlistName: String) =
        database.trackDao().deleteTracksForPlaylist(playlistName)
}
