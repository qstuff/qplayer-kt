package org.qstuff.qplayer.ui.player.jogwheel

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.floor

/**
 * Haptic feedback for the jog wheel: a click on touch-down and a light tick per "detent" while
 * spinning (every [DETENT_RAD], like the notches of a DJ controller's wheel), rate-limited so fast
 * spins don't turn into a continuous buzz.
 *
 * Uses the best effect the device supports: composition primitives (Android 11+, if supported),
 * else amplitude-scaled one-shots (Android 8+), else plain short pulses. Devices without a
 * vibrator ([isSupported] false) simply get nothing.
 */
class JogwheelHaptics(context: Context) {

    companion object {
        const val LEVEL_OFF = 0
        const val LEVEL_LIGHT = 1
        const val LEVEL_MEDIUM = 2
        const val LEVEL_STRONG = 3

        /** 24 detents per rotation. */
        private const val DETENT_RAD = 2 * PI / 24
        /** At most ~40 ticks per second. */
        private const val MIN_TICK_INTERVAL_MS = 25L

        // Per level (index = LEVEL_LIGHT..LEVEL_STRONG - 1)
        private val PRIMITIVE_SCALES = floatArrayOf(0.3f, 0.6f, 1.0f)
        private val AMPLITUDES = intArrayOf(70, 150, 255)
        private val TICK_MS = longArrayOf(6, 10, 14)
        private val CLICK_MS = longArrayOf(12, 20, 30)

        fun isSupported(context: Context): Boolean = vibratorOf(context)?.hasVibrator() == true

        private fun vibratorOf(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }

    private val vibrator: Vibrator? = vibratorOf(context)?.takeIf { it.hasVibrator() }

    private val usePrimitives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator?.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK
            ) == true

    private val useAmplitude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            vibrator?.hasAmplitudeControl() == true

    /** One of the LEVEL_* constants (the jog wheel vibration setting). */
    var level = LEVEL_MEDIUM

    private var lastDetent = 0
    private var lastTickTime = 0L

    /** Jog wheel touched. */
    fun onTouch() {
        lastDetent = 0
        vibrate(click = true)
    }

    /** Jog wheel turned: [rotationRad] is the rotation since touch-down. */
    fun onRotate(rotationRad: Double) {
        val detent = floor(rotationRad / DETENT_RAD).toInt()
        if (detent == lastDetent) return
        lastDetent = detent

        val now = SystemClock.uptimeMillis()
        if (now - lastTickTime < MIN_TICK_INTERVAL_MS) return
        lastTickTime = now
        vibrate(click = false)
    }

    private fun vibrate(click: Boolean) {
        val vibrator = vibrator ?: return
        if (level !in LEVEL_LIGHT..LEVEL_STRONG) return
        val i = level - 1

        when {
            usePrimitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val primitive = if (click) {
                    VibrationEffect.Composition.PRIMITIVE_CLICK
                } else VibrationEffect.Composition.PRIMITIVE_TICK
                vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(primitive, PRIMITIVE_SCALES[i])
                        .compose()
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                val ms = if (click) CLICK_MS[i] else TICK_MS[i]
                val amplitude = if (useAmplitude) AMPLITUDES[i] else VibrationEffect.DEFAULT_AMPLITUDE
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            }
            else -> {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (click) CLICK_MS[i] else TICK_MS[i])
            }
        }
    }
}
