package org.qstuff.qplayer.datasource.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File

/**
 * Created by Claus Chierici (github@antamauna.net) on 2/19/15
 *
 * Copyright (C) 2015 Claus Chierici, All rights reserved.
 */
// Room entity; also stored as JSON for the queue and the selected track (PreferencesDataSource).
@Serializable
@Entity(tableName = "Tracks")
data class Track(
        @PrimaryKey(autoGenerate = true)
        var track_id: Int = 0,
        var name: String = "",
        var uri: String = "",
        var duration: Long = 0,
        var playPosition: Long = 0,
        var cuePosition: Long = 0,
        var isAutoplay: Boolean = false,
        var playlistName: String = "") {

    @Ignore
    constructor(file: File): this() {
        this.uri = file.absolutePath
        this.name = file.name
    }

    @Ignore
    constructor(file: File, autoplay: Boolean): this() {
        this.uri = file.absolutePath
        this.name = file.name
        this.isAutoplay = autoplay
    }

    // Runtime-only playback status: neither in the database nor in the stored queue.
    @Ignore
    @Transient
    var trackStatus = TrackStatus.UNDEFINED

    enum class TrackStatus {
        UNDEFINED,
        LOADING,
        PREPARED,
        COMPLETED,
        ERROR
    }

    override fun toString() = "track: $name, status: ${trackStatus.name}, duration $duration, playPosition: $playPosition"
}
