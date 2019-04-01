package org.qstuff.qplayer.datasource.room

import androidx.room.*

import org.qstuff.qplayer.datasource.model.Track


/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 4/22/18
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
@Dao
interface TrackDao {

    @Query("SELECT * FROM Tracks; ")
    fun getAll(): List<Track>

    @Query("SELECT * FROM Tracks " +
            "WHERE playlistName = :playlistName " +
            "AND name IS NOT NULL;")
    fun getAllForPlaylist(playlistName: String): List<Track>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracklist: List<Track>)

    @Delete
    fun delete(tracks: List<Track>)

    @Query("DELETE FROM Tracks;")
    fun deleteAll()
}
