package org.qstuff.qplayer.ui.player.scratch

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.qstuff.qplayer.ui.player.audio.AudioDecoder
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.PI

/**
 * Vinyl-style scratching for the jog wheel's SCRATCH mode.
 *
 * ExoPlayer can't scratch (no reverse/zero speed, ~100-300 ms latency on speed changes), so this
 * engine takes over the audio only while the wheel is touched: the caller pauses ExoPlayer,
 * [start]s the engine at the playback position, feeds the wheel's rotation via [scratchTo] and
 * hands the final position back to ExoPlayer after [stop].
 *
 * - [prepare] decodes the track (via [AudioDecoder], in the background) to 16-bit PCM in a
 *   memory-mapped, already-unlinked cache file — off the Java heap, and nothing is left on disk.
 *   Scratching works as soon as the decoder has passed the playback position.
 * - A render thread plays the PCM through a low-latency [AudioTrack] at the device's native rate.
 *   Its read head follows the wheel with a critically damped follower (forward, backward or
 *   standing still) and reads with linear interpolation, so pitch follows speed like on vinyl.
 *
 * [start], [scratchTo], [stop], [prepare] and [release] are called from the main thread.
 */
class ScratchEngine(private val context: Context) {

    companion object {
        /** Audio covered by one wheel rotation at sensitivity 10 (= a 33⅓ rpm record). */
        private const val SECONDS_PER_ROTATION = 1.8
        /** How closely the read head follows the wheel; smooths the touch-event steps. */
        private const val FOLLOW_TIME_S = 0.025
        /** Max read-head speed in source frames per output frame. */
        private const val MAX_RATE = 8.0
        private const val BLOCK_FRAMES = 128
        /** Fade in/out at start/stop so the hand-over to/from ExoPlayer doesn't click. */
        private const val FADE_FRAMES = 256
        private const val DC_BLOCK_HZ = 20.0
        /** Buffer size used when the container doesn't report a duration. */
        private const val UNKNOWN_DURATION_S = 15 * 60.0
    }

    /** Decoded PCM of the prepared track: interleaved 16-bit, at most 2 channels. */
    private class PcmSource(
        val sampleRate: Int,
        val channels: Int,
        val capacityFrames: Int,
        private val buffer: ByteBuffer
    ) {
        /** Frames written so far. Written by the decoder, read by the render thread. */
        @Volatile var decodedFrames = 0

        fun put(frame: Int, channel: Int, value: Short) {
            buffer.putShort((frame * channels + channel) * 2, value)
        }

        fun sample(frame: Int, channel: Int): Float =
            buffer.getShort((frame * channels + channel) * 2) / 32768f
    }

    @Volatile private var source: PcmSource? = null
    @Volatile private var preparedUri: String? = null
    private var decodeJob: Job? = null

    private var audioTrack: AudioTrack? = null
    private var renderThread: Thread? = null

    @Volatile private var running = false
    @Volatile private var targetFrame = 0.0
    @Volatile private var positionFrame = 0.0
    private var startFrame = 0.0
    private var framesPerRadian = 0.0

    val isScratching: Boolean get() = running

    /** The read head's position while scratching / where the last scratch ended. */
    val positionMs: Long
        get() = source?.let { (positionFrame * 1000 / it.sampleRate).toLong() } ?: 0L

    /** Decode [uri] in the background so it can be scratched. No-op if already prepared. */
    fun prepare(scope: CoroutineScope, uri: String) {
        if (uri == preparedUri) return
        stop()
        decodeJob?.cancel()
        source = null
        preparedUri = uri
        decodeJob = scope.launch { decode(uri) }
    }

    /** Stop scratching, drop the decoded track and the audio output. */
    fun release() {
        stop()
        decodeJob?.cancel()
        decodeJob = null
        source = null
        preparedUri = null
        audioTrack?.release()
        audioTrack = null
    }

    /**
     * Start scratching at [positionMs]. Returns false if the track isn't decoded up to there yet
     * (or the audio output can't be opened) — the caller then leaves ExoPlayer alone.
     * [sensitivity] (the jog wheel setting, 10 = default) scales the audio per rotation.
     */
    fun start(positionMs: Long, sensitivity: Int): Boolean {
        if (running) return true
        val src = source ?: return false
        val frame = positionMs / 1000.0 * src.sampleRate
        if (frame >= src.decodedFrames - 1) return false
        val track = obtainAudioTrack(src) ?: return false

        startFrame = frame
        targetFrame = frame
        positionFrame = frame
        framesPerRadian = SECONDS_PER_ROTATION * sensitivity / 10.0 * src.sampleRate / (2 * PI)

        running = true
        track.play()
        renderThread = Thread({ render(src, track) }, "ScratchEngine").apply {
            isDaemon = true
            start()
        }
        return true
    }

    /** Move the record: [rotationRad] is the wheel's rotation since touch-down (clockwise > 0). */
    fun scratchTo(rotationRad: Double) {
        if (running) targetFrame = startFrame + rotationRad * framesPerRadian
    }

    /** Stop scratching (with a short fade-out); returns the final position in ms. */
    fun stop(): Long {
        if (running) {
            running = false
            renderThread?.join(200)
            renderThread = null
            audioTrack?.run {
                pause()
                flush()
            }
        }
        return positionMs
    }

    private suspend fun decode(uri: String) {
        var pcm: PcmSource? = null
        var writeFrame = 0

        AudioDecoder.decode(
            context, uri,
            onFormat = { sampleRate, channels, durationUs ->
                val outChannels = channels.coerceAtMost(2)
                val seconds = if (durationUs > 0) {
                    durationUs / 1_000_000.0 * 1.05 + 5   // margin for inexact (VBR) durations
                } else UNKNOWN_DURATION_S
                // One mapping is limited to 2 GB.
                val capacityFrames = (seconds * sampleRate).toLong()
                    .coerceAtMost(Int.MAX_VALUE / (2L * outChannels)).toInt()
                try {
                    val buffer = mapTempFile(capacityFrames.toLong() * outChannels * 2)
                    pcm = PcmSource(sampleRate, outChannels, capacityFrames, buffer).also {
                        if (preparedUri == uri) source = it
                    }
                } catch (e: Exception) {
                    Timber.e(e, "ScratchEngine: can't allocate the PCM buffer")
                }
            },
            onPcm = chunk@{ shorts, channels ->
                val p = pcm ?: return@chunk
                val n = shorts.limit()
                var i = shorts.position()
                while (i + channels <= n && writeFrame < p.capacityFrames) {
                    for (c in 0 until p.channels) p.put(writeFrame, c, shorts.get(i + c))
                    i += channels
                    writeFrame++
                }
                p.decodedFrames = writeFrame
            }
        )
    }

    /** A zero-filled, memory-mapped scratch buffer whose file is deleted right away. */
    private fun mapTempFile(bytes: Long): ByteBuffer {
        val file = File.createTempFile("scratch", ".pcm", context.cacheDir)
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(bytes)
                // The mapping stays valid after the channel is closed and the file is deleted.
                raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, bytes)
                    .order(ByteOrder.nativeOrder())
            }
        } finally {
            file.delete()
        }
    }

    private fun obtainAudioTrack(src: PcmSource): AudioTrack? {
        audioTrack?.let {
            if (it.channelCount == src.channels) return it
            it.release()
            audioTrack = null
        }
        return try {
            val outputRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            val channelMask = if (src.channels == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else AudioFormat.CHANNEL_OUT_STEREO
            val minBuffer = AudioTrack.getMinBufferSize(
                outputRate, channelMask, AudioFormat.ENCODING_PCM_FLOAT
            )
            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(outputRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(
                    if (minBuffer > 0) minBuffer else outputRate / 50 * 4 * src.channels
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            builder.build().also { audioTrack = it }
        } catch (e: Exception) {
            Timber.e(e, "ScratchEngine: can't open the audio output")
            null
        }
    }

    /** Render thread: moves the read head towards the wheel's target and writes the audio. */
    private fun render(src: PcmSource, track: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val channels = src.channels
        val out = FloatArray(BLOCK_FRAMES * channels)
        // Follower in output frames. damping = 4 / followFrames makes it critically damped, so the
        // record stops where the finger stops — no overshoot or wobble.
        val followFrames = FOLLOW_TIME_S * track.sampleRate
        val damping = 4.0 / followFrames
        val gainStep = 1f / FADE_FRAMES
        // DC blocker (one-pole high-pass): a record held still is silent instead of sitting on the
        // current sample's DC level. Seeded with the start sample, so touch-down doesn't click.
        val dcCoefficient = (1.0 - 2 * PI * DC_BLOCK_HZ / track.sampleRate).toFloat()

        var pos = positionFrame
        var rate = 0.0          // source frames per output frame
        var gain = 0f
        val lastIn = FloatArray(channels) { c -> src.sample(pos.toInt(), c) }
        val lastOut = FloatArray(channels)

        try {
            while (true) {
                val fadingOut = !running
                val target = targetFrame
                val last = (src.decodedFrames - 2).coerceAtLeast(0).toDouble()

                for (n in 0 until BLOCK_FRAMES) {
                    rate += ((target - pos) / followFrames - rate) * damping
                    rate = rate.coerceIn(-MAX_RATE, MAX_RATE)
                    pos += rate
                    if (pos < 0.0) {
                        pos = 0.0; rate = 0.0
                    } else if (pos > last) {
                        pos = last; rate = 0.0
                    }

                    gain = if (fadingOut) {
                        (gain - gainStep).coerceAtLeast(0f)
                    } else (gain + gainStep).coerceAtMost(1f)

                    val i = pos.toInt()
                    val f = (pos - i).toFloat()
                    for (c in 0 until channels) {
                        val a = src.sample(i, c)
                        val b = src.sample(i + 1, c)
                        val x = a + (b - a) * f
                        val y = x - lastIn[c] + dcCoefficient * lastOut[c]
                        lastIn[c] = x
                        lastOut[c] = y
                        out[n * channels + c] = y * gain
                    }
                }
                positionFrame = pos
                track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)

                if (fadingOut && gain <= 0f) break
            }
        } catch (e: Exception) {
            Timber.e(e, "ScratchEngine: render failed")
        }
    }
}
