package org.qstuff.qplayer.ui.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaveformPeaksTest {

    private val frames = WaveformPeaks.FRAMES_PER_PEAK.toLong()
    private val rate = 44_100.0

    @Test
    fun `overview takes the maximum per point and scales to 0 to 127`() {
        val peaks = WaveformPeaks(points = 2, expectedFrames = -1, sampleRate = rate)
        listOf(0, 16383, 100, WaveformPeaks.MAX_PEAK).forEach { peaks.add(it) }
        assertArrayEquals(byteArrayOf(63, 127), peaks.result()!!.overview)
    }

    @Test
    fun `detail keeps every peak, scaled, at sample rate per frames-per-peak`() {
        val peaks = WaveformPeaks(points = 2, expectedFrames = -1, sampleRate = rate)
        listOf(0, 16383, 100, WaveformPeaks.MAX_PEAK).forEach { peaks.add(it) }
        val result = peaks.result()!!
        assertArrayEquals(byteArrayOf(0, 63, 0, 127), result.detail)
        assertEquals((rate / WaveformPeaks.FRAMES_PER_PEAK).toFloat(), result.detailPeaksPerSecond, 0.001f)
    }

    @Test
    fun `nothing decoded gives no result`() {
        assertNull(WaveformPeaks(points = 10, expectedFrames = 100, sampleRate = rate).result())
    }

    @Test
    fun `previews grow from the left on the expected length`() {
        val peaks = WaveformPeaks(points = 100, expectedFrames = 100 * frames, sampleRate = rate)
        var preview: Waveform? = null
        repeat(50) { preview = peaks.add(WaveformPeaks.MAX_PEAK) ?: preview }
        val p = preview!!
        assertEquals(100, p.overview.size)
        assertEquals(127, p.overview[0].toInt())
        assertEquals(0, p.overview[99].toInt())   // not decoded yet
        assertEquals(50, p.detail.size)           // the decoded part
    }

    @Test
    fun `no previews without an expected length`() {
        val peaks = WaveformPeaks(points = 10, expectedFrames = -1, sampleRate = rate)
        repeat(1000) { assertNull(peaks.add(1)) }
    }

    @Test
    fun `result uses the decoded length even if the expected length was wrong`() {
        val peaks = WaveformPeaks(points = 4, expectedFrames = 100 * frames, sampleRate = rate)
        repeat(4) { peaks.add(WaveformPeaks.MAX_PEAK) }
        assertArrayEquals(byteArrayOf(127, 127, 127, 127), peaks.result()!!.overview)
    }
}
