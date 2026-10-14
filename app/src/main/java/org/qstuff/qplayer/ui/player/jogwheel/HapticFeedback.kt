package org.qstuff.qplayer.ui.player.jogwheel

/**
 * The device's vibration motor — platform independent. Android: AndroidHapticFeedback; iOS later:
 * UIImpactFeedbackGenerator / Core Haptics.
 */
interface HapticFeedback {

    enum class Effect { CLICK, TICK }

    /** Whether the device can vibrate at all. */
    val isSupported: Boolean

    /** Play [effect] at [strength] 1 (light) .. 3 (strong). */
    fun play(effect: Effect, strength: Int)
}
