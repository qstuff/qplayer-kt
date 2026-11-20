package org.qstuff.qplayer.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Vertical gap between a box-fitted text line and its box, top and bottom. */
val FittedTextVerticalPadding = 6.dp

/**
 * Font size → natural line height (ascent + descent) of the platform UI fonts (Roboto ≈ 1.17,
 * SF ≈ 1.19). The font size is the line height divided by this, so the glyphs fill the line.
 */
private const val FONT_LINE_HEIGHT_RATIO = 1.2f

/**
 * Text style for a single line that fills a box [boxHeight] tall, leaving
 * [FittedTextVerticalPadding] above and below. Only when [Dimens.fitTextToBoxes] (tablets) —
 * otherwise the default text style.
 *
 * The size is computed in dp (not scaled by the user's font scale), so the text always fits the
 * box, which itself doesn't grow with the font scale.
 */
@Composable
fun boxFittedTextStyle(boxHeight: Dp): TextStyle {
    if (!LocalDimens.current.fitTextToBoxes) return LocalTextStyle.current
    val lineHeight = with(LocalDensity.current) {
        (boxHeight - FittedTextVerticalPadding * 2).toSp()
    }
    return LocalTextStyle.current.merge(
        TextStyle(
            fontSize = lineHeight / FONT_LINE_HEIGHT_RATIO,
            lineHeight = lineHeight,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None
            )
        )
    )
}
