package org.qstuff.qplayer.ui.player.jogwheel

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class JogwheelHapticsTest {

    private class RecordingFeedback : HapticFeedback {
        override val isSupported = true
        val played = mutableListOf<Pair<HapticFeedback.Effect, Int>>()
        override fun play(effect: HapticFeedback.Effect, strength: Int) {
            played += effect to strength
        }
    }

    private val detent = 2 * PI / 24
    private var now = 0L
    private val feedback = RecordingFeedback()
    private val haptics = JogwheelHaptics(feedback) { now }

    @Test
    fun `click on touch, one tick per detent at the chosen strength`() {
        haptics.level = JogwheelHaptics.LEVEL_STRONG

        haptics.onTouch()
        haptics.onRotate(detent * 0.5)      // still in detent 0: nothing
        now += 100; haptics.onRotate(detent * 1.2)
        now += 100; haptics.onRotate(detent * 2.1)
        now += 100; haptics.onRotate(-detent * 0.5)  // back across 0: detent -1

        assertEquals(
            listOf(HapticFeedback.Effect.CLICK to 3) + List(3) { HapticFeedback.Effect.TICK to 3 },
            feedback.played
        )
    }

    @Test
    fun `fast spins are rate-limited to one tick per 25 ms`() {
        haptics.onTouch()
        feedback.played.clear()

        // 10 detents within 50 ms
        repeat(10) { i -> now += 5; haptics.onRotate(detent * (i + 1)) }

        assertEquals(2, feedback.played.size)
    }

    @Test
    fun `nothing at level off`() {
        haptics.level = JogwheelHaptics.LEVEL_OFF

        haptics.onTouch()
        haptics.onRotate(detent * 3)

        assertEquals(emptyList<Pair<HapticFeedback.Effect, Int>>(), feedback.played)
    }
}
