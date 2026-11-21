package org.qstuff.qplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class BeatDetectorTest {

    private val rate = 44_100f / WaveformPeaks.FRAMES_PER_PEAK   // ~86.13 peaks/s

    /**
     * A synthetic detail waveform: noise around 15, a hit on every beat (decaying over ~35 ms),
     * the first beat at [offsetSeconds], every 4th beat from beat [downbeat] louder.
     */
    private fun track(
        bpm: Float,
        seconds: Float = 180f,
        offsetSeconds: Float = 0.3f,
        downbeat: Int = 0,
        secondBpm: Float? = null,
        seed: Int = 1
    ): ByteArray {
        val random = Random(seed)
        val n = (seconds * rate).toInt()
        val out = IntArray(n) { 10 + random.nextInt(10) }
        var t = offsetSeconds
        var k = 0
        while (t < seconds) {
            val tempo = if (secondBpm != null && t > seconds / 2) secondBpm else bpm
            val i = (t * rate).toInt()
            val accent = if ((k - downbeat) % 4 == 0) 125 else 95
            for (d in 0..3) if (i + d < n) out[i + d] = maxOf(out[i + d], accent - d * 25)
            t += 60f / tempo
            k++
        }
        return ByteArray(n) { out[it].toByte() }
    }

    private fun assertGrid(bpm: Float, offset: Float, downbeat: Int) {
        val grid = BeatDetector.detect(track(bpm, offsetSeconds = offset, downbeat = downbeat), rate)
        assertNotNull("no grid at $bpm BPM", grid)
        grid!!
        assertEquals("bpm", bpm, grid.bpm, 0.05f)
        // first beat within one peak (~12 ms)
        val firstExpected = offset % (60f / bpm)
        assertEquals("first beat", firstExpected, grid.firstBeatSeconds, 1.5f / rate)
        assertEquals("downbeat", downbeat, grid.downbeatIndex)
    }

    @Test
    fun `steady beats are found at typical tempi`() {
        assertGrid(128f, offset = 0.3f, downbeat = 0)
        assertGrid(92f, offset = 0.11f, downbeat = 1)
        assertGrid(110f, offset = 0.45f, downbeat = 2)
        assertGrid(122.5f, offset = 0.21f, downbeat = 0)
        assertGrid(174f, offset = 0.05f, downbeat = 3)
    }

    @Test
    fun `the grid stays on the beats until the end of the track`() {
        val bpm = 123.4f
        val grid = BeatDetector.detect(track(bpm, seconds = 300f), rate)!!
        val lastBeatExpected = 0.3f + floor((300f - 0.3f) / (60f / bpm)) * (60f / bpm)
        val k = ((lastBeatExpected - grid.firstBeatSeconds) / grid.beatSeconds).toInt()
        val lastBeat = grid.firstBeatSeconds + k * grid.beatSeconds
        assert(abs(lastBeat - lastBeatExpected) < 0.03f) { "drift ${lastBeat - lastBeatExpected} s" }
    }

    @Test
    fun `dub echoes 1_5 beats after each hit don't make it 2 to 3 of the tempo`() {
        // 125 BPM with a loud echo of every hit a dotted quarter (1.5 beats) later
        val bpm = 125f
        val out = track(bpm, offsetSeconds = 0.2f).copyOf()
        var t = 0.2f + 1.5f * 60f / bpm
        while (t < out.size / rate) {
            val i = (t * rate).toInt()
            for (d in 0..3) if (i + d < out.size) out[i + d] = maxOf(out[i + d].toInt(), 110 - d * 25).toByte()
            t += 60f / bpm
        }
        assertEquals(bpm, BeatDetector.detect(out, rate)!!.bpm, 0.05f)
    }

    @Test
    fun `a slow track shows double tempo, in the DJ range`() {
        val grid = BeatDetector.detect(track(80f), rate)!!
        assertEquals(160f, grid.bpm, 0.05f)
    }

    @Test
    fun `noise without a beat is not reliable`() {
        val random = Random(7)
        val noise = ByteArray((180 * rate).toInt()) { (10 + random.nextInt(100)).toByte() }
        assertNull(BeatDetector.detect(noise, rate))
    }

    @Test
    fun `a tempo change halfway is not reliable`() {
        assertNull(BeatDetector.detect(track(120f, secondBpm = 128f), rate))
    }

    @Test
    fun `too short for a reliable tempo`() {
        assertNull(BeatDetector.detect(track(128f, seconds = 20f), rate))
    }

    private fun floor(x: Float) = kotlin.math.floor(x)
}
