package org.qstuff.qplayer.ui.player.waveform

import android.content.Context
import kotlinx.coroutines.isActive
import org.qstuff.qplayer.ui.player.audio.AudioDecoder
import kotlin.coroutines.coroutineContext

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

        // Reused copy of each PCM chunk: a bulk get() into a ShortArray is several times faster
        // than reading the ShortBuffer sample by sample (measured on the Tab A: 4.5 s → 0.8 s).
        var scratch = ShortArray(0)
        val decoded = AudioDecoder.decode(context, uri) { shorts, channels ->
            val count = shorts.remaining()
            if (scratch.size < count) scratch = ShortArray(count)
            shorts.get(scratch, 0, count)
            val buf = scratch
            var i = 0
            while (i < count) {
                var frameMax = 0
                val frameEnd = minOf(i + channels, count)
                while (i < frameEnd) {
                    val v = buf[i].toInt()
                    val s = if (v < 0) -v else v
                    if (s > frameMax) frameMax = s
                    i++
                }
                if (frameMax > currentPeak) currentPeak = frameMax
                if (++framesInPeak >= FRAMES_PER_PEAK) {
                    if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size * 2)
                    peaks[peakCount++] = currentPeak
                    currentPeak = 0
                    framesInPeak = 0
                }
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
