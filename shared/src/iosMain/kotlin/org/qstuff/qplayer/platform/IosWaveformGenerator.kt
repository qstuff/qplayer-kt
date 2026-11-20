package org.qstuff.qplayer.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.qstuff.qplayer.ui.player.WaveformGenerator
import org.qstuff.qplayer.ui.player.WaveformPeaks
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.Accelerate.vDSP_maxmgv
import co.touchlab.kermit.Logger
import kotlin.time.TimeSource

private val log = Logger.withTag("IosWaveformGenerator")

/**
 * iOS [WaveformGenerator]: reads the file with AVAudioFile (decoded float PCM) in blocks and builds
 * the overview like Android's WaveformAnalyzer — one peak per [WaveformPeaks.FRAMES_PER_PEAK]
 * frames into [WaveformPeaks], which downsamples them and produces the growing previews. The peaks
 * are found with Accelerate's vDSP_maxmgv.
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
            memScoped {
                val segmentMax = alloc<FloatVar>()
                // Read up to the file's length: a read at the end reports an error instead of 0 frames.
                while (currentCoroutineContext().isActive && file.framePosition < file.length) {
                    if (!file.readIntoBuffer(buffer, error = null)) break
                    val frames = buffer.frameLength.toInt()
                    if (frames == 0) break
                    val data = buffer.floatChannelData ?: return@withContext null
                    var i = 0
                    while (i < frames) {
                        // Up to the end of the current peak or of the block, whichever comes first.
                        val n = minOf(WaveformPeaks.FRAMES_PER_PEAK - framesInPeak, frames - i)
                        for (c in 0 until channels) {
                            // Accelerate: maximum magnitude of n samples (SIMD) instead of a
                            // per-sample Kotlin loop through the C pointer.
                            vDSP_maxmgv(data[c]!! + i.toLong(), 1, segmentMax.ptr, n.toULong())
                            if (segmentMax.value > currentPeak) currentPeak = segmentMax.value
                        }
                        i += n
                        framesInPeak += n
                        if (framesInPeak >= WaveformPeaks.FRAMES_PER_PEAK) {
                            peaks.add(toPeak(currentPeak))?.let(onProgress)
                            currentPeak = 0f
                            framesInPeak = 0
                        }
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
