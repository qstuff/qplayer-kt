package org.qstuff.qplayer.ui.player.waveform

import java.nio.ShortBuffer
import kotlin.math.abs

/**
 * Builds an overview waveform from decoded PCM, chunk by chunk: keeps a coarse intermediate peak
 * array (one peak per [FRAMES_PER_PEAK] frames, so memory is bounded and independent of the
 * reported duration) and downsamples it to the requested number of points at the end. Used by the
 * waveform decode and by the scratch engine's decode (one pass for both).
 */
class WaveformPeaks {

    private companion object {
        const val FRAMES_PER_PEAK = 1024
    }

    private var peaks = IntArray(4096)
    private var peakCount = 0
    private var framesInPeak = 0
    private var currentPeak = 0

    /** Add interleaved 16-bit samples between [samples]' position and limit (not modified). */
    fun add(samples: ShortBuffer, channels: Int) {
        val n = samples.limit()
        var i = samples.position()
        while (i < n) {
            var frameMax = 0
            var c = 0
            while (c < channels && i < n) {
                val s = abs(samples.get(i).toInt())
                if (s > frameMax) frameMax = s
                i++; c++
            }
            addFrame(frameMax)
        }
    }

    private fun addFrame(frameMax: Int) {
        if (frameMax > currentPeak) currentPeak = frameMax
        if (++framesInPeak >= FRAMES_PER_PEAK) {
            if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size * 2)
            peaks[peakCount++] = currentPeak
            currentPeak = 0
            framesInPeak = 0
        }
    }

    /** The waveform: [points] peak amplitudes 0..127 (max within each segment); null if empty. */
    fun overview(points: Int): ByteArray? {
        // a trailing partial peak counts too
        var count = peakCount
        var all = peaks
        if (framesInPeak > 0) {
            all = peaks.copyOf(peakCount + 1)
            all[count++] = currentPeak
        }
        if (count == 0) return null

        val out = ByteArray(points)
        for (b in 0 until points) {
            val start = (b.toLong() * count / points).toInt()
            var end = ((b + 1).toLong() * count / points).toInt()
            if (end <= start) end = (start + 1).coerceAtMost(count)
            var m = 0
            var k = start
            while (k < end) { if (all[k] > m) m = all[k]; k++ }
            out[b] = ((m * 127) / 32767).coerceIn(0, 127).toByte()
        }
        return out
    }
}
