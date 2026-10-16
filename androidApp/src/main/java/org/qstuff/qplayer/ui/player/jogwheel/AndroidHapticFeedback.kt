package org.qstuff.qplayer.ui.player.jogwheel

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Android [HapticFeedback]: uses the best effect the device supports — composition primitives
 * (Android 11+, if supported), else amplitude-scaled one-shots (Android 8+), else plain short
 * pulses. Devices without a vibrator get nothing.
 */
class AndroidHapticFeedback(context: Context) : HapticFeedback {

    private companion object {
        // Per strength (index = strength - 1)
        val PRIMITIVE_SCALES = floatArrayOf(0.3f, 0.6f, 1.0f)
        val AMPLITUDES = intArrayOf(70, 150, 255)
        val TICK_MS = longArrayOf(6, 10, 14)
        val CLICK_MS = longArrayOf(12, 20, 30)
    }

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }?.takeIf { it.hasVibrator() }

    override val isSupported: Boolean = vibrator != null

    private val usePrimitives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator?.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK
            ) == true

    private val useAmplitude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            vibrator?.hasAmplitudeControl() == true

    override fun play(effect: HapticFeedback.Effect, strength: Int) {
        val vibrator = vibrator ?: return
        if (strength !in 1..3) return
        val i = strength - 1
        val click = effect == HapticFeedback.Effect.CLICK

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
