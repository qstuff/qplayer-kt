package org.qstuff.qplayer.ui.player

import kotlin.math.max

/**
 * Builds the overview waveform from coarse peaks, shared by the platform generators: the decoder
 * loop reduces every [FRAMES_PER_PEAK] frames to one peak (0..[MAX_PEAK]) and [add]s it; at the
 * end [result] downsamples them to [points] bytes (0..127), the maximum within each segment.
 *
 * Memory is bounded: one Int per [FRAMES_PER_PEAK] frames, independent of the file format.
 *
 * @param expectedFrames the track length in frames, if known (> 0): enables the previews of
 *        [add], laid out on that length so the waveform grows from the left. The final [result]
 *        uses the frames actually decoded, so an inaccurate length doesn't distort it.
 */
class WaveformPeaks(private val points: Int, expectedFrames: Long) {

    companion object {
        const val FRAMES_PER_PEAK = 1024
        const val MAX_PEAK = 32767
        private const val PREVIEW_STEPS = 50
    }

    private var peaks = IntArray(4096)
    private var peakCount = 0
    private val expectedPeaks =
        if (expectedFrames > 0) ((expectedFrames + FRAMES_PER_PEAK - 1) / FRAMES_PER_PEAK).toInt() else 0
    private val previewEvery = max(1, expectedPeaks / PREVIEW_STEPS)

    /**
     * Adds the peak of the next [FRAMES_PER_PEAK] frames (the last one may cover fewer). Returns a
     * preview about every 2 % of the expected length, otherwise null.
     */
    fun add(peak: Int): ByteArray? {
        if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size * 2)
        peaks[peakCount++] = peak
        return if (expectedPeaks > 0 && peakCount < expectedPeaks && peakCount % previewEvery == 0) {
            downsample(expectedPeaks)
        } else null
    }

    /** The finished waveform, or null if nothing was decoded. */
    fun result(): ByteArray? = if (peakCount == 0) null else downsample(peakCount)

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
            out[b] = (m * 127 / MAX_PEAK).coerceIn(0, 127).toByte()
        }
        return out
    }
}
