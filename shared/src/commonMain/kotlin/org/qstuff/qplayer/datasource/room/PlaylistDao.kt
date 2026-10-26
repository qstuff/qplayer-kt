package org.qstuff.qplayer.datasource.room

import androidx.room.*

import org.qstuff.qplayer.datasource.model.Playlist

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 4/22/18
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
@Dao
interface PlaylistDao {

    @Query("SELECT * FROM Playlists " + "WHERE name IS NOT NULL;")
    suspend fun getAll(): List<Playlist>

    @Query("SELECT * FROM Playlists " +
            "WHERE name = :name " +
            "AND name IS NOT NULL;")
    suspend fun getPlaylistByName(name: String): Playlist

    /** Returns the playlist's row id (new if its id is 0). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(playlist: Playlist): Long

    @Delete
    suspend fun delete(playlist: Playlist)

    @Query("DELETE FROM Playlists WHERE  name = :name;")
    suspend fun delete(name: String)

    @Query("DELETE FROM Playlists;")
    suspend fun deleteAll()
}
