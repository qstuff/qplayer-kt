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
    fun getAll(): List<Playlist>

    @Query("SELECT * FROM Playlists " +
            "WHERE name = :name " +
            "AND name IS NOT NULL;")
    fun getPlaylistByName(name: String): Playlist

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(playlist: Playlist)

    @Delete
    fun delete(playlist: Playlist)

    @Query("DELETE FROM Playlists;")
    fun deleteAll()
}
