package org.qstuff.qplayer.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.qstuff.qplayer.ui.player.WaveformGenerator
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.Foundation.NSURL
import co.touchlab.kermit.Logger
import kotlin.math.abs
import kotlin.time.TimeSource

private val log = Logger.withTag("IosWaveformGenerator")

/**
 * iOS [WaveformGenerator]: reads the file with AVAudioFile (decoded float PCM) in blocks and builds
 * the overview like Android's WaveformAnalyzer — one peak per [FRAMES_PER_PEAK] frames, then the
 * maximum per output point, scaled to 0..127.
 */
@OptIn(ExperimentalForeignApi::class)
class IosWaveformGenerator(private val points: Int = 1000) : WaveformGenerator {

    private companion object {
        const val FRAMES_PER_PEAK = 1024
        const val BLOCK_FRAMES = 65_536u
    }

    override suspend fun generate(uri: String): ByteArray? = withContext(Dispatchers.Default) {
        val url = if (uri.startsWith("/")) NSURL.fileURLWithPath(IosPaths.toFilePath(uri)) else NSURL.URLWithString(uri)
        val file = url?.let { AVAudioFile(forReading = it, error = null) } ?: return@withContext null
        val buffer = AVAudioPCMBuffer(pCMFormat = file.processingFormat, frameCapacity = BLOCK_FRAMES)
        val channels = file.processingFormat.channelCount.toInt()

        var peaks = FloatArray(4096)
        var peakCount = 0
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
                if (++framesInPeak >= FRAMES_PER_PEAK) {
                    if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size * 2)
                    peaks[peakCount++] = currentPeak
                    currentPeak = 0f
                    framesInPeak = 0
                }
            }
        }
        if (!currentCoroutineContext().isActive) return@withContext null
        if (framesInPeak > 0) {
            if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size + 1)
            peaks[peakCount++] = currentPeak
        }
        if (peakCount == 0) return@withContext null
        log.d { "$peakCount peaks in ${started.elapsedNow()}: $uri" }

        ByteArray(points) { b ->
            val start = (b.toLong() * peakCount / points).toInt()
            var end = ((b + 1).toLong() * peakCount / points).toInt()
            if (end <= start) end = (start + 1).coerceAtMost(peakCount)
            var m = 0f
            for (k in start until end) if (peaks[k] > m) m = peaks[k]
            (m * 127).toInt().coerceIn(0, 127).toByte()
        }
    }
}
