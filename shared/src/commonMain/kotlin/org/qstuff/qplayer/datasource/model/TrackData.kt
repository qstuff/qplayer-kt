package org.qstuff.qplayer.datasource.model

/**
 * The waveform of [track]: [bytes] the overview (one peak 0..127 per point), [detail] one peak
 * 0..127 per WaveformPeaks.FRAMES_PER_PEAK frames, [detailPeaksPerSecond] of them per second of
 * the track (for the scrolling waveform). While decoding, both cover the decoded part only.
 */
class TrackData(
    var track: Track,
    var bytes: ByteArray?,
    val detail: ByteArray? = null,
    val detailPeaksPerSecond: Float = 0f
)
