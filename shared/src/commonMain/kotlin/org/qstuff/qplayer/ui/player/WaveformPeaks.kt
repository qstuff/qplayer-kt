package org.qstuff.qplayer.ui.player

import kotlin.math.max

/**
 * Builds the waveforms from peaks, shared by the platform generators: the decoder loop reduces
 * every [FRAMES_PER_PEAK] frames to one peak (0..[MAX_PEAK]) and [add]s it; [result] makes the
 * [Waveform] — the detail is these peaks as bytes (0..127), the overview downsamples them to
 * [points] bytes, the maximum within each segment.
 *
 * Memory is bounded: one Int per [FRAMES_PER_PEAK] frames, independent of the file format.
 *
 * @param expectedFrames the track length in frames, if known (> 0): enables the previews of
 *        [add], laid out on that length so the overview grows from the left. The final [result]
 *        uses the frames actually decoded, so an inaccurate length doesn't distort it.
 * @param sampleRate frames per second, for [Waveform.detailPeaksPerSecond].
 */
class WaveformPeaks(private val points: Int, expectedFrames: Long, sampleRate: Double) {

    companion object {
        /** ~86 peaks per second at 44.1 kHz: about 1 px per peak for 8 s on a tablet. */
        const val FRAMES_PER_PEAK = 512
        const val MAX_PEAK = 32767
        private const val PREVIEW_STEPS = 50
    }

    private val peaksPerSecond = (sampleRate / FRAMES_PER_PEAK).toFloat()
    private var peaks = IntArray(8192)
    private var peakCount = 0
    private val expectedPeaks =
        if (expectedFrames > 0) ((expectedFrames + FRAMES_PER_PEAK - 1) / FRAMES_PER_PEAK).toInt() else 0
    private val previewEvery = max(1, expectedPeaks / PREVIEW_STEPS)

    /**
     * Adds the peak of the next [FRAMES_PER_PEAK] frames (the last one may cover fewer). Returns a
     * preview about every 2 % of the expected length, otherwise null.
     */
    fun add(peak: Int): Waveform? {
        if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size * 2)
        peaks[peakCount++] = peak
        return if (expectedPeaks > 0 && peakCount < expectedPeaks && peakCount % previewEvery == 0) {
            Waveform(downsample(expectedPeaks), detail(), peaksPerSecond)
        } else null
    }

    /** The finished waveforms, or null if nothing was decoded. */
    fun result(): Waveform? =
        if (peakCount == 0) null else Waveform(downsample(peakCount), detail(), peaksPerSecond)

    private fun toByte(peak: Int): Byte = (peak * 127 / MAX_PEAK).coerceIn(0, 127).toByte()

    private fun detail(): ByteArray = ByteArray(peakCount) { toByte(peaks[it]) }

    /** [points] bytes from the peaks so far, laid out as if there were [totalPeaks]. */
    private fun downsample(totalPeaks: Int): ByteArray {
        val out = ByteArray(points)
        for (b in 0 until points) {
            val start = (b.toLong() * totalPeaks / points).toInt()
            if (start >= peakCount) break   // not decoded yet: stays 0
            var end = ((b + 1).toLong() * totalPeaks / points).toInt().coerceAtMost(peakCount)
            if (end <= start) end = start + 1
            var m = 0
            for (k in start until end) if (peaks[k] > m) m = peaks[k]
            out[b] = toByte(m)
        }
        return out
    }
}
