package org.qstuff.qplayer.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.qstuff.qplayer.ui.player.components.ClassicDeckMetrics

class ClassicDeckMetricsTest {

    @Test
    fun `the jog wheel fills the width beside fader, two button columns and the inset`() {
        val width = 392.dp
        val m = ClassicDeckMetrics(width, rowHeight = 32.dp)

        // fader + gap, two columns + gaps, 0.75 button widths of the third column, the wheel
        val used = m.faderWidth + m.gap + (m.unit + m.gap) * 2 + m.unit * 0.75f + m.jogSize
        assertEquals(width.value, used.value, 0.01f)
        // button rows: value, range, repeat/shuffle, cue, prev/next/play
        assertEquals((m.rowHeight * 3 + m.unit * 2 + m.gap * 4).value, m.buttonsHeight.value, 0.01f)
        // the wheel reaches one gap beyond them at the top (title bar) and bottom (track title)
        assertEquals((m.buttonsHeight + m.gap * 2).value, m.jogSize.value, 0.01f)
    }
}
