package org.qstuff.qplayer.datasource.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.ArrayList

/**
 * Created by Claus Chierici (github@antamauna.net) on 2/19/15
 *
 * Copyright (C) 2015 Claus Chierici, All rights reserved.
 */
@Entity(tableName = "Playlists")
data class Playlist(
        @PrimaryKey(autoGenerate = true)
        val playlist_id: Int,
        val name: String,
        @Embedded
        val trackList: ArrayList<Track>) {

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("PL Name: ")
        builder.append(name)
        builder.append("\n")
        builder.append(" Tracks: ")

        trackList.forEach {
            builder.append(it.toString())
            builder.append("\n")
        }

        return builder.toString()
    }
}
