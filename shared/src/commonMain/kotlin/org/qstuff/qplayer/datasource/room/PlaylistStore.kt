package org.qstuff.qplayer.datasource.room

import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track

/**
 * Where playlists and their tracks are stored (RoomDataSource). Tracks belong to a playlist by
 * [Track.playlistName]; saving a track with an existing [Track.track_id] replaces that row.
 */
interface PlaylistStore {

    suspend fun getAllPlaylists(): List<Playlist>

    /** Saves [playlist] and returns its id (a new one if [Playlist.playlist_id] is 0). */
    suspend fun addPlaylist(playlist: Playlist): Long

    suspend fun removePlaylist(playlist: Playlist)

    suspend fun getTracksForPlaylist(playlistName: String): List<Track>?

    suspend fun addTracks(tracklist: List<Track>)

    /** Removes these tracks (by [Track.track_id]) from their playlist. */
    suspend fun removeTracks(tracks: List<Track>)

    suspend fun removeTracksForPlaylist(playlistName: String)
}
