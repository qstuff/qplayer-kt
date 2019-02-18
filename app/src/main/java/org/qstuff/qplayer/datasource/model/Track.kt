package org.qstuff.qplayer.datasource.model


import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.io.File
import java.io.Serializable
import java.util.concurrent.TimeUnit


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
    @ColumnInfo(name = "duration")
    var duration: Long = 0
    @ColumnInfo(name = "playPosition")
    var playPosition: Long = 0
    @ColumnInfo(name = "cuePosition")
    var cuePosition: Long = 0
    @ColumnInfo(name = "autoplay")
    var isAutoplay: Boolean = false
    @ColumnInfo(name = "playlistName")
    var playlistName = ""

    @Ignore
    var trackStatus = TrackStatus.UNDEFINED


    @Ignore
    constructor(file: File) {
        this.uri = file.absolutePath
        this.name = file.name
    }

    @Ignore
    constructor(file: File, autoplay: Boolean) {
        this.uri = file.absolutePath
        this.name = file.name
        isAutoplay = autoplay
    }

    @Ignore
    constructor(name: String, uri: String) {
        this.uri = uri
        this.name = name
    }

    @Ignore
    constructor(name: String, uri: String, autoplay: Boolean) {
        this.uri = uri
        this.name = name
        this.isAutoplay = autoplay
    }

    enum class TrackStatus {
        UNDEFINED,
        LOADING,
        PREPARED,
        COMPLETED,
        ERROR
    }

    fun getDurationHumanReadable() =
        String.format("%02d:%02d:%02d",
            TimeUnit.MILLISECONDS.toHours(duration),
            TimeUnit.MILLISECONDS.toMinutes(duration) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(duration)), // The change is in this line
            TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)))

    override fun toString() = "track: $name, status: ${trackStatus.name}"
}
