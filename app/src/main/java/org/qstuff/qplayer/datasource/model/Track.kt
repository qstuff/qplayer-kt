package org.qstuff.qplayer.datasource.model


import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.io.File
import java.io.Serializable


/**
 * Created by Claus Chierici (github@antamauna.net) on 2/19/15
 *
 * Copyright (C) 2015 Claus Chierici, All rights reserved.
 */
//@Entity(tableName = "Tracks", foreignKeys = ForeignKey(entity = Playlist::class, parentColumns = arrayOf("name"), childColumns = arrayOf("playlist_name"), onDelete = CASCADE), indices = Index(value = "name"))
class Track : Serializable {

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    @ColumnInfo(name = "name")
    var name = ""

    @ColumnInfo(name = "uri")
    var uri = ""

    @ColumnInfo(name = "cuePosition")
    var cuePosition: Long = 0

    @ColumnInfo(name = "autoplay")
    var isAutoplay: Boolean = false

    @ColumnInfo(name = "playlist_name")
    var playlistName = ""

    @Ignore
    var trackStatus = TrackStatus.UNDEFINED

    @Ignore
    constructor(file: File) {
        this.uri = file.absolutePath
        this.name = file.name
    }

    @Ignore
    constructor(name: String, uri: String) {
        this.uri = uri
        this.name = name
        isAutoplay = false
    }

    @Ignore
    constructor(name: String, uri: String, autoplay: Boolean) {
        this.uri = uri
        this.name = name
        this.isAutoplay = autoplay
    }

    enum class TrackStatus(value: Int) {
        UNDEFINED(-1),
        LOADING(1),
        PREPARED(2),
        COMPLETED(3),
        ERROR(4)
    }

    override fun toString() = "track: $name, status: ${trackStatus.name}"
}
