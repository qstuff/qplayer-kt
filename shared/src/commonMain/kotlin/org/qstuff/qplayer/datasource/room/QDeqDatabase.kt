package org.qstuff.qplayer.datasource.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

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
@ConstructedBy(QDeqDatabaseConstructor::class)
abstract class QDeqDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
}

/** Room KMP: the compiler (KSP) generates the actual implementations per platform. */
@Suppress("KotlinNoActualForExpect")
expect object QDeqDatabaseConstructor : RoomDatabaseConstructor<QDeqDatabase> {
    override fun initialize(): QDeqDatabase
}
