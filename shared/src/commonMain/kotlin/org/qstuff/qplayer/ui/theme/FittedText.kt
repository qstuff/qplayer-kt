package org.qstuff.qplayer.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/** Gap between a box-fitted text line and its box (top and bottom; for chips also start/end). */
val FittedTextPadding = 6.dp

/** Smallest size a box-fitted chip label shrinks to when it's too wide for its chip. */
private val MIN_CHIP_FONT_SIZE = 8.sp

/**
 * Font size → natural line height (ascent + descent) of the platform UI fonts (Roboto ≈ 1.17,
 * SF ≈ 1.19). The font size is the line height divided by this, so the glyphs fill the line.
 */
private const val FONT_LINE_HEIGHT_RATIO = 1.2f

/**
 * Text style for a single line that fills a box [boxHeight] tall, leaving
 * [FittedTextPadding] above and below. Only when [Dimens.fitTextToBoxes] (tablets) —
 * otherwise the default text style.
 *
 * The size is computed in dp (not scaled by the user's font scale), so the text always fits the
 * box, which itself doesn't grow with the font scale.
 */
@Composable
fun boxFittedTextStyle(boxHeight: Dp): TextStyle {
    if (!LocalDimens.current.fitTextToBoxes) return LocalTextStyle.current
    val lineHeight = with(LocalDensity.current) {
        (boxHeight - FittedTextPadding * 2).toSp()
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

/**
 * A single-line chip label. Tablets ([Dimens.fitTextToBoxes]): as large as a box [boxHeight]
 * tall allows (see [boxFittedTextStyle]), keeps [FittedTextPadding] to the start/end edges and
 * shrinks when it's too wide for the chip — chips are narrow, so long labels end up smaller than
 * short ones. Phone: plain [Text].
 */
@Composable
fun BoxFittedText(
    text: String,
    color: Color,
    boxHeight: Dp,
    modifier: Modifier = Modifier
) {
    if (!LocalDimens.current.fitTextToBoxes) {
        Text(text = text, color = color, modifier = modifier)
        return
    }
    val style = boxFittedTextStyle(boxHeight)
    // StepBased wants max − min divisible by the step: round the max down to whole 0.5 sp.
    val maxFontSize = maxOf(floor(style.fontSize.value * 2f) / 2f, MIN_CHIP_FONT_SIZE.value * 2f).sp
    BasicText(
        text = text,
        style = style.merge(TextStyle(color = color, textAlign = TextAlign.Center)),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(
            minFontSize = MIN_CHIP_FONT_SIZE,
            maxFontSize = maxFontSize,
            stepSize = 0.5.sp
        ),
        modifier = modifier.padding(horizontal = FittedTextPadding)
    )
}
