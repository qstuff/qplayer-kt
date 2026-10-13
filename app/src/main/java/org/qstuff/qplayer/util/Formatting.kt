package org.qstuff.qplayer.util

import java.text.DecimalFormatSymbols
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Number/time formatting without String.format (not multiplatform). Only [localeDecimalSeparator]
 * is platform-specific — it becomes an expect/actual in the KMP module.
 */
object Formatting {

    /** Like String.format("%.1f"): one decimal, half-up, with the device's decimal separator. */
    fun oneDecimal(value: Float): String {
        val tenths = (abs(value.toDouble()) * 10).roundToLong()
        val sign = if (value < 0) "-" else ""
        return "$sign${tenths / 10}${localeDecimalSeparator()}${tenths % 10}"
    }

    /** "hh:mm:ss", zero-padded, like String.format("%02d:%02d:%02d"). */
    fun hoursMinutesSeconds(millis: Long): String {
        val totalSeconds = millis / 1000
        return "${pad2(totalSeconds / 3600)}:${pad2(totalSeconds % 3600 / 60)}:${pad2(totalSeconds % 60)}"
    }

    private fun pad2(value: Long) = value.toString().padStart(2, '0')

    private fun localeDecimalSeparator(): Char = DecimalFormatSymbols.getInstance().decimalSeparator
}
