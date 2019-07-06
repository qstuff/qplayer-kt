package org.qstuff.qplayer.datasource.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Created by Claus Chierici (github@antamauna.net) on 2/19/15
 *
 * Copyright (C) 2015 Claus Chierici, All rights reserved.
 */
@Entity(tableName = "Playlists")
data class Playlist(
        @PrimaryKey(autoGenerate = true)
        var playlist_id: Int,
        var name: String,
        @Ignore
        var trackList: List<Track>) {

    constructor() : this(0, "", listOf())

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
