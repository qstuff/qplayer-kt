package org.qstuff.qplayer.ui.player

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A constant-tempo beat grid in track time: beats at [firstBeatSeconds] + k · [beatSeconds]
 * (k ≥ 0), bars (4 beats) starting at beat [downbeatIndex] (0..3) and every 4th beat after it.
 */
class BeatGrid(val bpm: Float, val firstBeatSeconds: Float, val downbeatIndex: Int) {
    val beatSeconds: Float get() = 60f / bpm
}

/**
 * Finds tempo and beats of a track from its detail waveform (one peak 0..127 per
 * 1/[peaksPerSecond] s, see [WaveformPeaks]) — made for music with a steady beat (dance music,
 * most pop/rock).
 *
 * 1. Onsets: the rises of the log-compressed level (kicks, snares, …), minus their local mean.
 * 2. Tempo: the period within one octave, [MIN_BPM]..[MAX_BPM], so half / double / 2:3 tempi (dub
 *    delays echo 1.5 beats later) can't win — whose multiples (1–8 beats) line up best
 *    in the onsets' autocorrelation — measured in the middle of each half of the track, which
 *    must agree — then refined to 0.01 BPM by how well a whole-track grid hits
 *    the onsets.
 * 3. Phase: the grid offset that hits the most onset strength; the downbeat is the beat of the
 *    four with the strongest onsets (a guess — most tracks put the strongest accents on the "1").
 *
 * Returns null when the beat isn't reliable — too short, no clear period, or the two halves of
 * the track disagree on the tempo (live recordings, tempo changes); the caller then shows a
 * fixed time window instead of bars.
 */
object BeatDetector {

    /** The usual DJ range: slower tracks show double tempo, faster ones half (one octave). */
    const val MIN_BPM = 88f
    const val MAX_BPM = 176f
    /** Seconds analysed for the tempo in each half of the track (its middle part). */
    private const val WINDOW_SECONDS = 90f
    private const val MIN_SECONDS = 30f
    private const val MULTIPLES = 8
    private const val COARSE_STEP = 0.05f
    /** Best autocorrelation score vs. the average score over all tempi. */
    private const val MIN_CONTRAST = 1.3f
    /** The halves' tempi may differ by this fraction. */
    private const val MAX_HALF_DIFFERENCE = 0.01f

    /** Whether [detect] can find anything: at least [MIN_SECONDS] of detail. */
    fun canDetect(detail: ByteArray, peaksPerSecond: Float): Boolean =
        peaksPerSecond > 0f && detail.size >= MIN_SECONDS * peaksPerSecond

    fun detect(detail: ByteArray, peaksPerSecond: Float): BeatGrid? {
        if (!canDetect(detail, peaksPerSecond)) return null
        val onsets = onsets(detail, peaksPerSecond)

        // Tempo from up to WINDOW_SECONDS in the middle of each half; a steady tempo means both
        // agree (live recordings and tempo changes don't). Bounded work for long tracks.
        val half = onsets.size / 2
        val window = minOf(half, (WINDOW_SECONDS * peaksPerSecond).toInt())
        val firstStart = (half - window) / 2
        val secondStart = half + (onsets.size - half - window) / 2
        val first = coarseTempo(onsets, firstStart, firstStart + window, peaksPerSecond) ?: return null
        val second = coarseTempo(onsets, secondStart, secondStart + window, peaksPerSecond) ?: return null
        if (abs(first - second) > MAX_HALF_DIFFERENCE * first) return null
        val bpm = (first + second) / 2

        val (fineBpm, phase) = refine(onsets, bpm, peaksPerSecond)
        val period = 60f * peaksPerSecond / fineBpm
        return BeatGrid(
            bpm = fineBpm,
            firstBeatSeconds = phase / peaksPerSecond,
            downbeatIndex = downbeat(onsets, phase, period)
        )
    }

    /** Onset strength per peak: positive rise of the log level, minus its local (±0.2 s) mean. */
    internal fun onsets(detail: ByteArray, peaksPerSecond: Float): FloatArray {
        val n = detail.size
        val level = FloatArray(n) { ln(1f + detail[it].toInt().coerceIn(0, 127) / 12.7f) }
        val rise = FloatArray(n)
        for (i in 1 until n) rise[i] = max(0f, level[i] - level[i - 1])
        val radius = max(1, (0.2f * peaksPerSecond).roundToInt())
        val out = FloatArray(n)
        var sum = 0f
        for (i in 0 until minOf(radius, n)) sum += rise[i]
        for (i in 0 until n) {
            if (i + radius < n) sum += rise[i + radius]
            if (i - radius - 1 >= 0) sum -= rise[i - radius - 1]
            val count = minOf(n - 1, i + radius) - max(0, i - radius) + 1
            out[i] = max(0f, rise[i] - sum / count)
        }
        return out
    }

    /** Best tempo of onsets[from, to) by the autocorrelation at 1–[MULTIPLES] beats, or null. */
    private fun coarseTempo(onsets: FloatArray, from: Int, to: Int, rate: Float): Float? {
        val maxLag = (MULTIPLES * 60f * rate / MIN_BPM).toInt() + 2
        val n = to - from
        if (n <= maxLag * 2) return null
        val ac = FloatArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var s = 0f
            for (i in from until to - lag) s += onsets[i] * onsets[i + lag]
            ac[lag] = s / (n - lag)
        }
        fun score(bpm: Float): Float {
            val period = 60f * rate / bpm
            var s = 0f
            for (m in 1..MULTIPLES) s += interpolate(ac, m * period)
            return s
        }
        var best = 0f
        var bestBpm = 0f
        var total = 0f
        var count = 0
        var bpm = MIN_BPM
        while (bpm <= MAX_BPM) {
            val s = score(bpm)
            total += s
            count++
            if (s > best) { best = s; bestBpm = bpm }
            bpm += COARSE_STEP
        }
        if (count == 0 || best <= 0f || best < MIN_CONTRAST * (total / count)) return null
        return bestBpm
    }

    /**
     * Fine tempo (±0.15 BPM around [bpm], steps of 0.01) and phase (in peaks): the whole-track
     * grid that collects the most onset strength — small tempo errors add up over minutes, so
     * this matters for the grid's end.
     */
    private fun refine(onsets: FloatArray, bpm: Float, rate: Float): Pair<Float, Float> {
        var bestScore = -1f
        var bestBpm = bpm
        var bestPhase = 0f
        var candidate = bpm - 0.15f
        while (candidate <= bpm + 0.15f + 1e-4f) {
            val period = 60f * rate / candidate
            val (phase, score) = bestPhase(onsets, period)
            if (score > bestScore) { bestScore = score; bestBpm = candidate; bestPhase = phase }
            candidate += 0.01f
        }
        return (bestBpm * 100).roundToInt() / 100f to bestPhase
    }

    /** The offset in [0, period) (half-peak steps) whose grid sums the most onset strength. */
    private fun bestPhase(onsets: FloatArray, period: Float): Pair<Float, Float> {
        var best = -1f
        var bestPhase = 0f
        var phase = 0f
        while (phase < period) {
            var s = 0f
            var t = phase
            while (t < onsets.size - 1) { s += interpolate(onsets, t); t += period }
            if (s > best) { best = s; bestPhase = phase }
            phase += 0.5f
        }
        return bestPhase to best
    }

    /** Which of the four beats (0..3 from the first) has the strongest onsets: the bar's "1". */
    private fun downbeat(onsets: FloatArray, phase: Float, period: Float): Int {
        val sums = FloatArray(4)
        var k = 0
        var t = phase
        while (t < onsets.size - 1) { sums[k % 4] += interpolate(onsets, t); k++; t += period }
        return sums.indices.maxBy { sums[it] }
    }

    private fun interpolate(values: FloatArray, x: Float): Float {
        if (x < 0f) return 0f
        val i = floor(x).toInt()
        if (i >= values.size - 1) return if (i == values.size - 1) values[i] else 0f
        val f = x - i
        return values[i] * (1 - f) + values[i + 1] * f
    }
}
