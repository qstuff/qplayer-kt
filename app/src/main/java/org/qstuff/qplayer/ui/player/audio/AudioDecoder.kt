package org.qstuff.qplayer.ui.player.audio

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
import java.nio.ShortBuffer
import kotlin.coroutines.coroutineContext

/**
 * Decodes the first audio track of a file to 16-bit PCM using only platform codecs
 * (MediaExtractor + MediaCodec) and streams it to the caller chunk by chunk.
 *
 * Runs on Dispatchers.Default and cooperates with cancellation (checks isActive), so the caller
 * can cancel it when the track changes.
 *
 * Assumes 16-bit little-endian PCM output, which is what Android decoders produce by default.
 */
object AudioDecoder {

    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /**
     * @param onFormat called once before the first PCM chunk with the decoder's output format;
     *                 [durationUs] is the container's duration or -1 if unknown.
     * @param onPcm    called for each decoded chunk: interleaved samples between the buffer's
     *                 position and limit, [channels] samples per frame.
     * @return true if the whole file was decoded, false on error or cancellation.
     */
    suspend fun decode(
        context: Context,
        uri: String,
        onFormat: (sampleRate: Int, channels: Int, durationUs: Long) -> Unit = { _, _, _ -> },
        onPcm: (pcm: ShortBuffer, channels: Int) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
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
            } ?: return@withContext false

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext false
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else -1L
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var formatReported = false

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

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
                        val outputFormat = codec.outputFormat
                        channels = outputFormat
                            .getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> { /* no output yet */ }
                    else -> if (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && info.size > 0) {
                            if (!formatReported) {
                                onFormat(sampleRate, channels, durationUs)
                                formatReported = true
                            }
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            onPcm(outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(), channels)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    }
                }
            }
            sawOutputEOS
        } catch (e: Exception) {
            Timber.e(e, "AudioDecoder.decode failed for %s", uri)
            false
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
