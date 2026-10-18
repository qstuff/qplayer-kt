package org.qstuff.qplayer.platform

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qstuff.qplayer.ui.player.Scratcher
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSourceNode
import platform.CoreAudioTypes.AudioBufferList
import kotlin.concurrent.Volatile
import kotlin.math.PI

private val log = Logger.withTag("IosScratchEngine")

/**
 * Vinyl-style scratching on iOS — the same algorithm as Android's ScratchEngine.
 *
 * - [prepare] decodes the track (AVAudioFile, in the background) to interleaved 16-bit PCM in one
 *   native memory block (~10 MB per minute, outside the Kotlin heap; the length is known up front).
 *   It's freed explicitly when the track changes or the engine is released. Scratching works as
 *   soon as the decoder has passed the playback position.
 * - An AVAudioSourceNode renders the output: its read head follows the wheel with a critically
 *   damped follower and reads with linear interpolation (pitch follows speed like on vinyl),
 *   through a DC blocker, with short fades at start and stop.
 * - The render callback runs on the real-time audio thread: it reads only native memory and
 *   primitive fields and allocates (almost) nothing, so the garbage collector isn't involved.
 * - The output engine runs only while scratching (plus a short idle time), so it doesn't keep the
 *   app awake in the background.
 *
 * [start], [scratchTo], [stop], [prepare] and [release] are called from the main thread.
 */
@OptIn(ExperimentalForeignApi::class)
class IosScratchEngine : Scratcher {

    private companion object {
        /** Audio covered by one wheel rotation at sensitivity 10 (= a 33⅓ rpm record). */
        const val SECONDS_PER_ROTATION = 1.8
        /** How closely the read head follows the wheel; smooths the touch-event steps. */
        const val FOLLOW_TIME_S = 0.025
        /** Max read-head speed in source frames per output frame. */
        const val MAX_RATE = 8.0
        /** Fade in/out at start/stop so the hand-over to/from the player doesn't click. */
        const val FADE_FRAMES = 256
        const val DC_BLOCK_HZ = 20.0
        const val DECODE_BLOCK_FRAMES = 32_768u
        /** The output engine stops this long after the last scratch. */
        const val IDLE_STOP_MS = 2_000L
    }

    /** Decoded PCM of the prepared track: interleaved 16-bit, at most 2 channels. */
    private class PcmSource(val sampleRate: Double, val channels: Int, val capacityFrames: Long) {
        val samples: CPointer<ShortVar> = nativeHeap.allocArray(capacityFrames * channels)
        /** Frames decoded so far. Written by the decoder, read by the render thread. */
        @Volatile var decodedFrames: Long = 0
        private var freed = false

        fun free() {
            if (!freed) nativeHeap.free(samples.rawValue)
            freed = true
        }
    }

    private val main = MainScope()

    private var preparedUri: String? = null
    @Volatile private var source: PcmSource? = null
    private var decodeJob: Job? = null

    private var engine: AVAudioEngine? = null
    private var engineChannels = 0
    private var outputRate = 48_000.0
    private var idleStopJob: Job? = null

    // Shared with the render thread. `running` is written last on start, so the render thread
    // sees the initialised state.
    @Volatile private var running = false
    @Volatile private var targetFrame = 0.0
    @Volatile private var positionFrame = 0.0
    private var startFrame = 0.0
    private var framesPerRadian = 0.0

    // Render-thread state
    private var pos = 0.0
    private var rate = 0.0
    private var gain = 0f
    private var lastIn0 = 0f
    private var lastIn1 = 0f
    private var lastOut0 = 0f
    private var lastOut1 = 0f
    private var followFrames = 1.0
    private var damping = 1.0
    private var dcCoefficient = 1f

    override val isScratching: Boolean get() = running

    override val positionMs: Long
        get() = source?.let { (positionFrame * 1000 / it.sampleRate).toLong() } ?: 0L

    override fun prepare(scope: CoroutineScope, uri: String) {
        if (uri == preparedUri) return
        stop()
        dropSource()
        preparedUri = uri

        val file = openAudioFile(uri) ?: return
        val format = file.processingFormat
        val src = PcmSource(format.sampleRate, format.channelCount.toInt().coerceIn(1, 2), file.length)
        source = src
        decodeJob = scope.launch { decode(file, src) }
    }

    override fun release() {
        stop()
        dropSource()
        preparedUri = null
        idleStopJob?.cancel()
        engine?.stop()
        engine = null
        engineChannels = 0
    }

    override fun start(positionMs: Long, sensitivity: Int): Boolean {
        if (running) return true
        val src = source ?: return false
        val frame = positionMs / 1000.0 * src.sampleRate
        if (frame >= src.decodedFrames - 1) return false
        if (!startEngine(src.channels)) return false

        startFrame = frame
        targetFrame = frame
        positionFrame = frame
        framesPerRadian = SECONDS_PER_ROTATION * sensitivity / 10.0 * src.sampleRate / (2 * PI)

        // Follower in output frames. damping = 4 / followFrames makes it critically damped, so the
        // record stops where the finger stops — no overshoot or wobble.
        followFrames = FOLLOW_TIME_S * outputRate
        damping = 4.0 / followFrames
        // DC blocker (one-pole high-pass): a record held still is silent instead of sitting on the
        // current sample's DC level. Seeded with the start sample, so touch-down doesn't click.
        dcCoefficient = (1.0 - 2 * PI * DC_BLOCK_HZ / outputRate).toFloat()
        pos = frame
        rate = 0.0
        gain = 0f
        lastIn0 = sample(src, frame.toLong(), 0)
        lastIn1 = sample(src, frame.toLong(), 1)
        lastOut0 = 0f
        lastOut1 = 0f

        idleStopJob?.cancel()
        running = true
        return true
    }

    override fun scratchTo(rotationRad: Double) {
        if (running) targetFrame = startFrame + rotationRad * framesPerRadian
    }

    override fun stop(): Long {
        if (running) {
            running = false     // the render thread fades out
            scheduleIdleStop()
        }
        return positionMs
    }

    /** Decode [file] into [src] block by block (background), publishing the decoded length. */
    private suspend fun decode(file: AVAudioFile, src: PcmSource) = withContext(Dispatchers.Default) {
        val buffer = AVAudioPCMBuffer(pCMFormat = file.processingFormat, frameCapacity = DECODE_BLOCK_FRAMES)
        val channels = src.channels
        val fileChannels = file.processingFormat.channelCount.toInt()
        while (isActive && file.framePosition < file.length) {
            if (!file.readIntoBuffer(buffer, error = null)) break
            val frames = buffer.frameLength.toInt()
            if (frames == 0) break
            val data = buffer.floatChannelData ?: break
            val base = src.decodedFrames
            val count = minOf(frames.toLong(), src.capacityFrames - base).toInt()
            if (count <= 0) break
            for (c in 0 until channels) {
                val input = data[c.coerceAtMost(fileChannels - 1)]!!
                var out = base * channels + c
                for (i in 0 until count) {
                    val s = (input[i] * 32767f).toInt().coerceIn(-32768, 32767)
                    src.samples[out] = s.toShort()
                    out += channels
                }
            }
            src.decodedFrames = base + count
        }
    }

    /** Forget the prepared track; its memory is freed once its decoder has finished. */
    private fun dropSource() {
        val old = source ?: return
        source = null
        val job = decodeJob
        decodeJob = null
        main.launch {
            job?.cancel()
            job?.join()
            old.free()
        }
    }

    private fun startEngine(channels: Int): Boolean {
        val current = engine
        if (current != null && engineChannels == channels) {
            if (current.running) return true
            return current.startAndReturnError(null)
        }
        current?.stop()

        val newEngine = AVAudioEngine()
        outputRate = newEngine.outputNode.outputFormatForBus(0u).sampleRate.takeIf { it > 0 } ?: 48_000.0
        val format = AVAudioFormat(standardFormatWithSampleRate = outputRate, channels = channels.toUInt())
        val node = AVAudioSourceNode(format = format) { isSilence, _, frameCount, outputData ->
            render(isSilence, frameCount.toInt(), outputData)
            0
        }
        newEngine.attachNode(node)
        newEngine.connect(node, to = newEngine.mainMixerNode, format = format)
        newEngine.prepare()
        if (!newEngine.startAndReturnError(null)) {
            log.e { "can't start the scratch output" }
            return false
        }
        engine = newEngine
        engineChannels = channels
        return true
    }

    private fun scheduleIdleStop() {
        idleStopJob?.cancel()
        idleStopJob = main.launch {
            delay(IDLE_STOP_MS)
            if (!running) engine?.pause()
        }
    }

    private fun sample(src: PcmSource, frame: Long, channel: Int): Float {
        val decoded = src.decodedFrames
        if (decoded <= 0) return 0f
        val f = frame.coerceIn(0, decoded - 1)
        val c = channel.coerceAtMost(src.channels - 1)
        return src.samples[f * src.channels + c] / 32768f
    }

    /** Render thread: moves the read head towards the wheel's target and writes the audio. */
    private fun render(isSilence: CPointer<BooleanVar>?, frameCount: Int, outputData: CPointer<AudioBufferList>?) {
        val list = outputData?.pointed ?: return
        val buffers = list.mBuffers
        val out0 = buffers[0].mData?.reinterpret<FloatVar>() ?: return
        val out1 = if (list.mNumberBuffers > 1u) buffers[1].mData?.reinterpret<FloatVar>() else null

        val src = source
        val active = running
        if (src == null || (!active && gain <= 0f)) {
            for (n in 0 until frameCount) {
                out0[n] = 0f
                out1?.set(n, 0f)
            }
            isSilence?.pointed?.value = true
            return
        }

        val target = targetFrame
        val last = (src.decodedFrames - 2).coerceAtLeast(0).toDouble()
        val gainStep = 1f / FADE_FRAMES

        for (n in 0 until frameCount) {
            rate += ((target - pos) / followFrames - rate) * damping
            rate = rate.coerceIn(-MAX_RATE, MAX_RATE)
            pos += rate
            if (pos < 0.0) {
                pos = 0.0; rate = 0.0
            } else if (pos > last) {
                pos = last; rate = 0.0
            }

            gain = if (active) (gain + gainStep).coerceAtMost(1f) else (gain - gainStep).coerceAtLeast(0f)

            val i = pos.toLong()
            val f = (pos - i).toFloat()

            val a0 = sample(src, i, 0)
            val x0 = a0 + (sample(src, i + 1, 0) - a0) * f
            val y0 = x0 - lastIn0 + dcCoefficient * lastOut0
            lastIn0 = x0
            lastOut0 = y0
            out0[n] = y0 * gain

            if (out1 != null) {
                val a1 = sample(src, i, 1)
                val x1 = a1 + (sample(src, i + 1, 1) - a1) * f
                val y1 = x1 - lastIn1 + dcCoefficient * lastOut1
                lastIn1 = x1
                lastOut1 = y1
                out1[n] = y1 * gain
            }
        }
        positionFrame = pos
    }
}
