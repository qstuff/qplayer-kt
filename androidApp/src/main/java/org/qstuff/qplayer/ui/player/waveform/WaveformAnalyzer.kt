package org.qstuff.qplayer.ui.player.waveform

import android.content.Context
import kotlinx.coroutines.isActive
import org.qstuff.qplayer.ui.player.WaveformPeaks
import org.qstuff.qplayer.ui.player.audio.AudioDecoder
import kotlin.coroutines.coroutineContext

/**
 * Generates an overview waveform (one peak-amplitude byte per output point) from an audio file,
 * decoded by [AudioDecoder] (platform codecs only — no native/3rd-party deps).
 *
 * Cooperates with cancellation, so the caller can cancel it when the track changes. Streams the
 * decoded PCM: one peak per [WaveformPeaks.FRAMES_PER_PEAK] frames goes into [WaveformPeaks],
 * which also produces the growing previews for [onProgress].
 */
object WaveformAnalyzer {

    private const val DEFAULT_POINTS = 1000

    suspend fun analyze(
        context: Context,
        uri: String,
        points: Int = DEFAULT_POINTS,
        onProgress: (ByteArray) -> Unit = {}
    ): ByteArray? {
        var peaks = WaveformPeaks(points, expectedFrames = -1)
        var framesInPeak = 0
        var currentPeak = 0
        // Reused copy of each PCM chunk: a bulk get() into a ShortArray is several times faster
        // than reading the ShortBuffer sample by sample (measured on the Tab A: 4.5 s → 0.8 s).
        var scratch = ShortArray(0)

        val decoded = AudioDecoder.decode(
            context, uri,
            onFormat = { sampleRate, _, durationUs ->
                val frames = if (durationUs > 0) durationUs * sampleRate / 1_000_000 else -1L
                peaks = WaveformPeaks(points, frames)
            }
        ) { shorts, channels ->
            val count = shorts.remaining()
            if (scratch.size < count) scratch = ShortArray(count)
            shorts.get(scratch, 0, count)
            val buf = scratch
            var i = 0
            while (i < count) {
                val frameEnd = minOf(i + channels, count)
                while (i < frameEnd) {
                    val v = buf[i].toInt()
                    val s = if (v < 0) -v else v
                    if (s > currentPeak) currentPeak = s
                    i++
                }
                if (++framesInPeak >= WaveformPeaks.FRAMES_PER_PEAK) {
                    peaks.add(currentPeak)?.let(onProgress)
                    currentPeak = 0
                    framesInPeak = 0
                }
            }
        }

        if (!decoded || !coroutineContext.isActive) return null
        if (framesInPeak > 0) peaks.add(currentPeak)   // trailing partial peak
        return peaks.result()
    }
}
