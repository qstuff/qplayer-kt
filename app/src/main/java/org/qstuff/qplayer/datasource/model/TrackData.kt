package org.qstuff.qplayer.datasource.model


class TrackData {

    var bytes: ByteArray

    constructor() {
        this.bytes = byteArrayOf()
    }

    constructor(bytes: ByteArray) {
        this.bytes = bytes
    }
}
