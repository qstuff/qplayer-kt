package org.qstuff.qplayer.ui.player.jogwheel

import kotlin.math.PI
import kotlin.math.floor
import kotlin.time.TimeSource

/**
 * Haptic feedback for the jog wheel: a click on touch-down and a light tick per "detent" while
 * spinning (every [DETENT_RAD], like the notches of a DJ controller's wheel), rate-limited so fast
 * spins don't turn into a continuous buzz. Platform independent; the vibration itself comes from
 * [feedback]. [nowMs] is a monotonic clock (replaceable in tests).
 */
class JogwheelHaptics(
    private val feedback: HapticFeedback,
    private val nowMs: () -> Long = monotonicMs()
) {

    companion object {
        const val LEVEL_OFF = 0
        const val LEVEL_LIGHT = 1
        const val LEVEL_MEDIUM = 2
        const val LEVEL_STRONG = 3

        /** 24 detents per rotation. */
        private const val DETENT_RAD = 2 * PI / 24
        /** At most ~40 ticks per second. */
        private const val MIN_TICK_INTERVAL_MS = 25L

        private fun monotonicMs(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }

    /** One of the LEVEL_* constants (the jog wheel vibration setting); also the strength. */
    var level = LEVEL_MEDIUM

    private var lastDetent = 0
    // Long.MIN_VALUE / 2: the first tick is never rate-limited (and no overflow when subtracting).
    private var lastTickTime = Long.MIN_VALUE / 2

    /** Jog wheel touched. */
    fun onTouch() {
        lastDetent = 0
        vibrate(HapticFeedback.Effect.CLICK)
    }

    /** Jog wheel turned: [rotationRad] is the rotation since touch-down. */
    fun onRotate(rotationRad: Double) {
        val detent = floor(rotationRad / DETENT_RAD).toInt()
        if (detent == lastDetent) return
        lastDetent = detent

        val now = nowMs()
        if (now - lastTickTime < MIN_TICK_INTERVAL_MS) return
        lastTickTime = now
        vibrate(HapticFeedback.Effect.TICK)
    }

    private fun vibrate(effect: HapticFeedback.Effect) {
        if (level in LEVEL_LIGHT..LEVEL_STRONG) feedback.play(effect, strength = level)
    }
}
