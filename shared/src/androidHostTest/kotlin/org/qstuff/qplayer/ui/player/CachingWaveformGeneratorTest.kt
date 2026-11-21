package org.qstuff.qplayer.ui.player

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CachingWaveformGeneratorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val waveform = Waveform(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6, 7), 86.13f)

    private fun assertSame(expected: Waveform, actual: Waveform?) {
        assertArrayEquals(expected.overview, actual!!.overview)
        assertArrayEquals(expected.detail, actual.detail)
        assertEquals(expected.detailPeaksPerSecond, actual.detailPeaksPerSecond, 0f)
    }

    private inner class CountingGenerator : WaveformGenerator {
        var calls = 0
        override suspend fun generate(uri: String, onProgress: (Waveform) -> Unit): Waveform {
            calls++
            onProgress(Waveform(byteArrayOf(1), byteArrayOf(), 86f))
            return waveform
        }
    }

    @Test
    fun `second request is served from disk, also by a new instance`() = runTest {
        val dir = tmp.root.resolve("waveforms").path
        val delegate = CountingGenerator()
        val first = CachingWaveformGenerator(delegate, dir) { 42L }
        assertSame(waveform, first.generate("/music/a.mp3"))

        val afterRestart = CachingWaveformGenerator(delegate, dir) { 42L }
        val progress = mutableListOf<Waveform>()
        assertSame(waveform, afterRestart.generate("/music/a.mp3") { progress += it })
        assertEquals(1, delegate.calls)
        assertEquals(0, progress.size)   // cached: no previews needed
    }

    @Test
    fun `a different file size decodes again`() = runTest {
        val dir = tmp.root.path
        val delegate = CountingGenerator()
        var size = 42L
        val generator = CachingWaveformGenerator(delegate, dir) { size }
        generator.generate("/music/a.mp3")
        size = 43L
        generator.generate("/music/a.mp3")
        assertEquals(2, delegate.calls)
    }

    @Test
    fun `file names differ by id and size`() {
        val a = CachingWaveformGenerator.cacheFileName("/a.mp3", 1)
        assertEquals(a, CachingWaveformGenerator.cacheFileName("/a.mp3", 1))
        assertNotEquals(a, CachingWaveformGenerator.cacheFileName("/b.mp3", 1))
        assertNotEquals(a, CachingWaveformGenerator.cacheFileName("/a.mp3", 2))
        assertEquals(20, a.length)   // 16 hex digits + ".wf2"
    }

    @Test
    fun `the file format round-trips and rejects other files`() {
        val bytes = CachingWaveformGenerator.encode(waveform)
        assertSame(waveform, CachingWaveformGenerator.decode(bytes))
        assertNull(CachingWaveformGenerator.decode(ByteArray(1000) { 64 }))      // old overview-only file
        assertNull(CachingWaveformGenerator.decode(bytes.copyOf(bytes.size - 1))) // cut off
    }
}
