package org.qstuff.qplayer.ui.player

/**
 * The waveforms of one track, from one decode: [overview] one peak amplitude (0..127) per point
 * of the whole track, [detail] one per [WaveformPeaks.FRAMES_PER_PEAK] frames — [detailPeaksPerSecond]
 * of them per second of the track — for the scrolling waveform.
 */
class Waveform(
    val overview: ByteArray,
    val detail: ByteArray,
    val detailPeaksPerSecond: Float
)

/**
 * Creates the [Waveform] of an audio file, or null if it can't be decoded. Platform-specific
 * decoding (Android: MediaCodec, iOS: AVAudioFile).
 */
interface WaveformGenerator {
    /**
     * @param onProgress receives growing previews while decoding (the overview has its final
     *        size with the points not decoded yet 0; the detail covers the decoded part),
     *        roughly every 2 % of the track. Called on a background thread, before [generate]
     *        returns.
     */
    suspend fun generate(uri: String, onProgress: (Waveform) -> Unit = {}): Waveform?
}
