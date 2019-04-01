package org.qstuff.qplayer.datasource.room

import androidx.room.Database
import androidx.room.RoomDatabase

import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 4/22/18
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */

@Database(entities = [
    Playlist::class,
    Track::class
], version = 2, exportSchema = false)
abstract class QDeqDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
}
