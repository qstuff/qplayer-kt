package org.qstuff.qplayer.platform

import org.qstuff.qplayer.ui.player.jogwheel.HapticFeedback
import platform.CoreHaptics.CHHapticEngine
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/**
 * iOS [HapticFeedback] via the Taptic Engine: a rigid impact for CLICK (touching the wheel), a
 * light one for TICK (detents), at an intensity per strength. The generators are kept prepared,
 * so fast ticks follow with little latency. iPads and the simulator have no Taptic Engine
 * ([isSupported] false — the setting is then disabled, like on Android devices without vibrator).
 *
 * Called on the main thread (jog wheel gestures).
 */
class IosHapticFeedback : HapticFeedback {

    private companion object {
        // Per strength (index = strength - 1)
        val CLICK_INTENSITIES = doubleArrayOf(0.5, 0.75, 1.0)
        val TICK_INTENSITIES = doubleArrayOf(0.3, 0.6, 1.0)
    }

    override val isSupported: Boolean = CHHapticEngine.capabilitiesForHardware().supportsHaptics

    private val click = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
    private val tick = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)

    override fun play(effect: HapticFeedback.Effect, strength: Int) {
        if (!isSupported || strength !in 1..3) return
        val i = strength - 1
        val (generator, intensity) = when (effect) {
            HapticFeedback.Effect.CLICK -> click to CLICK_INTENSITIES[i]
            HapticFeedback.Effect.TICK -> tick to TICK_INTENSITIES[i]
        }
        generator.impactOccurredWithIntensity(intensity)
        generator.prepare()     // ready for the next one
    }
}
