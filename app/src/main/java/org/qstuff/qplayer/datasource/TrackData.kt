package org.qstuff.qplayer.datasource


class TrackData {

    var bytes: ByteArray

    constructor() {
        this.bytes = byteArrayOf()
    }

    constructor(bytes: ByteArray) {
        this.bytes = bytes
    }
}
