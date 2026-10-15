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
 * decoded PCM into [WaveformPeaks]. In scratch mode the scratch engine's decode delivers the
 * waveform instead (one decode pass for both).
 */
object WaveformAnalyzer {

    const val DEFAULT_POINTS = 1000

    suspend fun analyze(context: Context, uri: String, points: Int = DEFAULT_POINTS): ByteArray? {
        val peaks = WaveformPeaks()
        val decoded = AudioDecoder.decode(context, uri) { shorts, channels -> peaks.add(shorts, channels) }
        if (!decoded || !coroutineContext.isActive) return null
        return peaks.overview(points)
    }
}
