package org.qstuff.qplayer.ui.player.waveform

import android.content.Context
import kotlinx.coroutines.isActive
import org.qstuff.qplayer.ui.player.audio.AudioDecoder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Generates an overview waveform (one peak-amplitude byte per output point) from an audio file,
 * decoded by [AudioDecoder] (platform codecs only — no native/3rd-party deps).
 *
 * Cooperates with cancellation, so the caller can cancel it when the track changes. Streams the
 * decoded PCM: it keeps a coarse intermediate peak array (one peak per [FRAMES_PER_PEAK] frames,
 * so memory is bounded and independent of the reported duration), then downsamples that to
 * [points] peaks in 0..127.
 */
object WaveformAnalyzer {

    private const val DEFAULT_POINTS = 1000
    private const val FRAMES_PER_PEAK = 1024

    suspend fun analyze(context: Context, uri: String, points: Int = DEFAULT_POINTS): ByteArray? {
        // Coarse intermediate peaks (one per FRAMES_PER_PEAK frames), grown as needed.
        var peaks = IntArray(4096)
        var peakCount = 0
        var framesInPeak = 0
        var currentPeak = 0

        fun pushFramePeak(frameMax: Int) {
            if (frameMax > currentPeak) currentPeak = frameMax
            if (++framesInPeak >= FRAMES_PER_PEAK) {
                if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size * 2)
                peaks[peakCount++] = currentPeak
                currentPeak = 0
                framesInPeak = 0
            }
        }

        val decoded = AudioDecoder.decode(context, uri) { shorts, channels ->
            val n = shorts.limit()
            var i = shorts.position()
            while (i < n) {
                var frameMax = 0
                var c = 0
                while (c < channels && i < n) {
                    val s = abs(shorts.get(i).toInt())
                    if (s > frameMax) frameMax = s
                    i++; c++
                }
                pushFramePeak(frameMax)
            }
        }

        if (!decoded) return null

        // flush a trailing partial peak
        if (framesInPeak > 0) {
            if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size + 1)
            peaks[peakCount++] = currentPeak
        }

        if (!coroutineContext.isActive || peakCount == 0) return null

        // Downsample coarse peaks → `points` bytes (0..127), max within each segment.
        val out = ByteArray(points)
        for (b in 0 until points) {
            val start = (b.toLong() * peakCount / points).toInt()
            var end = ((b + 1).toLong() * peakCount / points).toInt()
            if (end <= start) end = (start + 1).coerceAtMost(peakCount)
            var m = 0
            var k = start
            while (k < end) { if (peaks[k] > m) m = peaks[k]; k++ }
            out[b] = ((m * 127) / 32767).coerceIn(0, 127).toByte()
        }
        return out
    }
}
