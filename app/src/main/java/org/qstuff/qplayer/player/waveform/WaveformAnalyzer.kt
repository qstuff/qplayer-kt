package org.qstuff.qplayer.player.waveform

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Generates an overview waveform (one peak-amplitude byte per output point) from an audio file,
 * using only platform codecs (MediaExtractor + MediaCodec) — no native/3rd-party deps.
 *
 * Runs on Dispatchers.Default and cooperates with cancellation (checks isActive), so the caller
 * can cancel it when the track changes. Streams the decoded PCM: it keeps a coarse intermediate
 * peak array (one peak per [FRAMES_PER_PEAK] frames, so memory is bounded and independent of the
 * reported duration), then downsamples that to [points] peaks in 0..127.
 *
 * Assumes 16-bit little-endian PCM output, which is what Android decoders produce by default.
 */
object WaveformAnalyzer {

    private const val DEFAULT_POINTS = 1000
    private const val FRAMES_PER_PEAK = 1024
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    suspend fun analyze(context: Context, uri: String, points: Int = DEFAULT_POINTS): ByteArray? =
        withContext(Dispatchers.Default) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                if (uri.startsWith("/")) {
                    extractor.setDataSource(uri)
                } else {
                    extractor.setDataSource(context, Uri.parse(uri), null)
                }

                val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                    extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                        ?.startsWith("audio/") == true
                } ?: return@withContext null

                extractor.selectTrack(trackIndex)
                val inputFormat = extractor.getTrackFormat(trackIndex)
                val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
                var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(inputFormat, null, null, 0)
                codec.start()

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

                val info = MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false

                while (!sawOutputEOS && coroutineContext.isActive) {
                    if (!sawInputEOS) {
                        val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inBuf = codec.getInputBuffer(inIndex)
                            val sampleSize = inBuf?.let { extractor.readSampleData(it, 0) } ?: -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    when (val outIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            channels = codec.outputFormat
                                .getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> { /* no output yet */ }
                        else -> if (outIndex >= 0) {
                            val outBuf = codec.getOutputBuffer(outIndex)
                            if (outBuf != null && info.size > 0) {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                val shorts = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val n = shorts.remaining()
                                var i = 0
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
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawOutputEOS = true
                            }
                        }
                    }
                }

                // flush a trailing partial peak
                if (framesInPeak > 0 && peakCount < Int.MAX_VALUE) {
                    if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size + 1)
                    peaks[peakCount++] = currentPeak
                }

                if (!coroutineContext.isActive || peakCount == 0) return@withContext null

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
                out
            } catch (e: Exception) {
                Timber.e(e, "WaveformAnalyzer.analyze failed for %s", uri)
                null
            } finally {
                try { codec?.stop() } catch (_: Exception) {}
                try { codec?.release() } catch (_: Exception) {}
                try { extractor.release() } catch (_: Exception) {}
            }
        }
}
