package org.qstuff.qplayer.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Formatting must produce exactly what the former String.format calls did. */
class FormattingTest {

    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    private val pitchValues = listOf(
        0f, 0.04f, -0.04f, 0.05f, 0.15f, 0.25f, 1.24f, 1.25f, -2.55f, 7.99f, 9.96f, -10f, 12.34f, -15f,
        33.333f, 99.95f, -100f
    ) + (-1000..1000 step 7).map { (it - 500).toFloat() / 62.5f }

    @Test
    fun `one decimal matches String format in German`() = checkOneDecimal(Locale.GERMANY)

    @Test
    fun `one decimal matches String format in English`() = checkOneDecimal(Locale.US)

    private fun checkOneDecimal(locale: Locale) {
        Locale.setDefault(locale)
        pitchValues.forEach { value ->
            assertEquals("value $value", String.format("%02.01f", value), Formatting.oneDecimal(value))
        }
    }

    @Test
    fun `hours minutes seconds matches String format`() {
        listOf(0L, 999L, 1_000L, 59_999L, 60_000L, 593_841L, 3_599_999L, 3_600_000L, 36_000_000L + 61_500L)
            .forEach { millis ->
                val seconds = millis / 1000
                val expected = String.format("%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
                assertEquals("millis $millis", expected, Formatting.hoursMinutesSeconds(millis))
            }
    }
}
