package org.qstuff.qplayer.datasource.model



import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.ArrayList

/**
 * Created by Claus Chierici (github@antamauna.net) on 2/19/15
 *
 * Copyright (C) 2015 Claus Chierici, All rights reserved.
 */
@Entity(tableName = "Playlists")
class Playlist : Serializable {

    @ColumnInfo(name = "playlist_id")
    var playlistId: Int = 0

    @Ignore
    var trackList = ArrayList<Track>()
        set(trackList) {
            this.trackList.clear()
            field = trackList
        }

    @PrimaryKey
    @ColumnInfo(name = "name")
    var name = ""


    @Ignore
    constructor(name: String) {
        this.name = name
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("PL Name: ")
        builder.append(name)
        builder.append("\n")

        return builder.toString()
    }
}
