package org.qstuff.qplayer.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.qstuff.qplayer.ui.player.WaveformGenerator
import org.qstuff.qplayer.ui.player.WaveformPeaks
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import co.touchlab.kermit.Logger
import kotlin.math.abs
import kotlin.time.TimeSource

private val log = Logger.withTag("IosWaveformGenerator")

/**
 * iOS [WaveformGenerator]: reads the file with AVAudioFile (decoded float PCM) in blocks and builds
 * the overview like Android's WaveformAnalyzer — one peak per [WaveformPeaks.FRAMES_PER_PEAK]
 * frames into [WaveformPeaks], which downsamples them and produces the growing previews.
 */
@OptIn(ExperimentalForeignApi::class)
class IosWaveformGenerator(private val points: Int = 1000) : WaveformGenerator {

    private companion object {
        const val BLOCK_FRAMES = 65_536u
    }

    override suspend fun generate(uri: String, onProgress: (ByteArray) -> Unit): ByteArray? =
        withContext(Dispatchers.Default) {
            val file = openAudioFile(uri) ?: return@withContext null
            val buffer = AVAudioPCMBuffer(pCMFormat = file.processingFormat, frameCapacity = BLOCK_FRAMES)
            val channels = file.processingFormat.channelCount.toInt()
            val peaks = WaveformPeaks(points, expectedFrames = file.length)

            var framesInPeak = 0
            var currentPeak = 0f

            val started = TimeSource.Monotonic.markNow()
            // Read up to the file's length: a read at the end reports an error instead of 0 frames.
            while (currentCoroutineContext().isActive && file.framePosition < file.length) {
                if (!file.readIntoBuffer(buffer, error = null)) break
                val frames = buffer.frameLength.toInt()
                if (frames == 0) break
                val data = buffer.floatChannelData ?: return@withContext null
                for (i in 0 until frames) {
                    for (c in 0 until channels) {
                        val s = abs(data[c]!![i])
                        if (s > currentPeak) currentPeak = s
                    }
                    if (++framesInPeak >= WaveformPeaks.FRAMES_PER_PEAK) {
                        peaks.add(toPeak(currentPeak))?.let(onProgress)
                        currentPeak = 0f
                        framesInPeak = 0
                    }
                }
            }
            if (!currentCoroutineContext().isActive) return@withContext null
            if (framesInPeak > 0) peaks.add(toPeak(currentPeak))
            log.d { "waveform in ${started.elapsedNow()}: $uri" }
            peaks.result()
        }

    /** Float sample magnitude (0..1) → [WaveformPeaks]' integer scale. */
    private fun toPeak(magnitude: Float): Int =
        (magnitude * WaveformPeaks.MAX_PEAK).toInt().coerceIn(0, WaveformPeaks.MAX_PEAK)
}
