package org.qstuff.qplayer.ui.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaveformPeaksTest {

    private val frames = WaveformPeaks.FRAMES_PER_PEAK.toLong()

    @Test
    fun `result takes the maximum per point and scales to 0 to 127`() {
        val peaks = WaveformPeaks(points = 2, expectedFrames = -1)
        listOf(0, 16383, 100, WaveformPeaks.MAX_PEAK).forEach { peaks.add(it) }
        assertArrayEquals(byteArrayOf(63, 127), peaks.result())
    }

    @Test
    fun `nothing decoded gives no result`() {
        assertNull(WaveformPeaks(points = 10, expectedFrames = 100).result())
    }

    @Test
    fun `previews grow from the left on the expected length`() {
        val peaks = WaveformPeaks(points = 100, expectedFrames = 100 * frames)
        var preview: ByteArray? = null
        repeat(50) { preview = peaks.add(WaveformPeaks.MAX_PEAK) ?: preview }
        val p = preview!!
        assertEquals(100, p.size)
        assertEquals(127, p[0].toInt())
        assertEquals(0, p[99].toInt())   // not decoded yet
    }

    @Test
    fun `no previews without an expected length`() {
        val peaks = WaveformPeaks(points = 10, expectedFrames = -1)
        repeat(1000) { assertNull(peaks.add(1)) }
    }

    @Test
    fun `result uses the decoded length even if the expected length was wrong`() {
        val peaks = WaveformPeaks(points = 4, expectedFrames = 100 * frames)
        repeat(4) { peaks.add(WaveformPeaks.MAX_PEAK) }
        assertArrayEquals(byteArrayOf(127, 127, 127, 127), peaks.result())
    }
}
