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
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.PI

/**
 * Vinyl-style scratching for the jog wheel's SCRATCH mode.
 *
 * ExoPlayer can't scratch (no reverse/zero speed, ~100-300 ms latency on speed changes), so this
 * engine takes over the audio only while the wheel is touched: the caller pauses ExoPlayer,
 * [start]s the engine at the playback position, feeds the wheel's rotation via [scratchTo] and
 * hands the final position back to ExoPlayer after [stop].
 *
 * - [prepare] decodes the track (via [AudioDecoder], in the background) to 16-bit PCM in a cache
 *   file (~10 MB per minute) — off the Java heap. Only the prepared track's file exists; it is
 *   unlinked right after creation and closed explicitly when the track changes or the engine is
 *   released, so its disk space is freed immediately (and by the OS if the process dies).
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
        /** Frames the read head can move within one block, either way (+ interpolation). */
        private val WINDOW_REACH = (MAX_RATE * BLOCK_FRAMES).toInt() + 2
    }

    /**
     * Decoded PCM of the prepared track (interleaved 16-bit, at most 2 channels) in a cache file.
     *
     * The file is unlinked as soon as it's open, so it only exists while the source is open: [close]
     * frees the space at once (a mapped file would only be freed whenever the GC unmaps it, which let
     * deleted files of previously loaded tracks pile up to gigabytes). The decoder appends, the
     * render thread reads windows around the read head; both use positional I/O, which may run
     * concurrently.
     */
    private class PcmSource(cacheDir: File, val sampleRate: Int, val channels: Int) : Closeable {

        // Open first, then unlink: the open file stays usable, its name is gone.
        private val file = File.createTempFile("scratch", ".pcm", cacheDir).let { tmp ->
            RandomAccessFile(tmp, "rw").also { tmp.delete() }
        }
        private val channel = file.channel
        private val frameBytes = channels * 2

        /** Frames written so far. Written by the decoder, read by the render thread. */
        @Volatile var decodedFrames = 0

        /** Decoder side: appends the frames between the buffer's position and limit. */
        fun append(frames: ByteBuffer) {
            var offset = decodedFrames.toLong() * frameBytes
            val count = frames.remaining() / frameBytes
            while (frames.hasRemaining()) offset += channel.write(frames, offset)
            decodedFrames += count
        }

        /**
         * Render side: reads frames [first] until [first] + count (clamped to what's decoded) into
         * [into], interleaved, via [buffer] and its short [view] (reused, so the audio thread
         * doesn't allocate); returns the number of frames read.
         */
        fun read(first: Int, count: Int, buffer: ByteBuffer, view: ShortBuffer, into: ShortArray): Int {
            val frames = count.coerceAtMost(decodedFrames - first).coerceAtLeast(0)
            buffer.clear().limit(frames * frameBytes)
            var offset = first.toLong() * frameBytes
            while (buffer.hasRemaining()) {
                val n = channel.read(buffer, offset)
                if (n < 0) break
                offset += n
            }
            view.clear()
            view.get(into, 0, frames * channels)
            return frames
        }

        override fun close() {
            try { file.close() } catch (_: Exception) {}
        }
    }

    // `source` and `preparedUri` change together under `lock` (main thread vs. decoder).
    private val lock = Any()
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
        closeSource(newUri = uri)
        decodeJob = scope.launch { decode(uri) }
    }

    /** Stop scratching, drop the decoded track (freeing its disk space) and the audio output. */
    fun release() {
        stop()
        decodeJob?.cancel()
        decodeJob = null
        closeSource(newUri = null)
        audioTrack?.release()
        audioTrack = null
    }

    /** Closes (and so deletes) the current track's PCM file; a still running decoder then fails. */
    private fun closeSource(newUri: String?) {
        val old = synchronized(lock) {
            preparedUri = newUri
            source.also { source = null }
        }
        old?.close()
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
        // Reused staging buffer for the frames of one decoder chunk (grown as needed).
        var staging = ByteBuffer.allocateDirect(64 * 1024).order(ByteOrder.nativeOrder())

        try {
            AudioDecoder.decode(
                context, uri,
                onFormat = { sampleRate, channels, _ ->
                    val created = PcmSource(context.cacheDir, sampleRate, channels.coerceAtMost(2))
                    val current = synchronized(lock) {
                        (preparedUri == uri).also { if (it) source = created }
                    }
                    if (current) pcm = created else created.close()
                },
                onPcm = chunk@{ shorts, channels ->
                    val p = pcm ?: return@chunk
                    val frames = (shorts.limit() - shorts.position()) / channels
                    val bytes = frames * p.channels * 2
                    if (staging.capacity() < bytes) {
                        staging = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
                    }
                    staging.clear()
                    val out = staging.asShortBuffer()
                    var i = shorts.position()
                    repeat(frames) {
                        for (c in 0 until p.channels) out.put(shorts.get(i + c))
                        i += channels
                    }
                    staging.limit(bytes)
                    p.append(staging)
                }
            )
        } finally {
            // Superseded meanwhile (track changed / released): don't leave its file open.
            pcm?.let { p -> if (synchronized(lock) { source !== p }) p.close() }
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
        // The decoded frames around the read head, re-read for every block: the head can move at
        // most WINDOW_REACH frames either way within one block.
        val windowFrames = 2 * WINDOW_REACH + 1
        val windowBytes = ByteBuffer.allocateDirect(windowFrames * channels * 2)
            .order(ByteOrder.nativeOrder())
        val windowShorts = windowBytes.asShortBuffer()
        val window = ShortArray(windowFrames * channels)
        var windowStart = 0
        var windowCount = 0

        fun loadWindow(center: Int) {
            windowStart = (center - WINDOW_REACH).coerceAtLeast(0)
            windowCount = src.read(windowStart, windowFrames, windowBytes, windowShorts, window)
        }

        fun sample(frame: Int, channel: Int): Float {
            if (windowCount == 0) return 0f
            val i = (frame - windowStart).coerceIn(0, windowCount - 1)
            return window[i * channels + channel] / 32768f
        }

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
        val lastIn = FloatArray(channels)
        val lastOut = FloatArray(channels)

        try {
            loadWindow(pos.toInt())
            for (c in 0 until channels) lastIn[c] = sample(pos.toInt(), c)

            while (true) {
                val fadingOut = !running
                val target = targetFrame
                val last = (src.decodedFrames - 2).coerceAtLeast(0).toDouble()
                loadWindow(pos.toInt())

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
                        val a = sample(i, c)
                        val b = sample(i + 1, c)
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
