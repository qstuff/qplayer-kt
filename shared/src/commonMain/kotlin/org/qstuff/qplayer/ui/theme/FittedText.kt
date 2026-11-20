package org.qstuff.qplayer.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/** Gap between a box-fitted text line and its box (top and bottom; for chips also start/end). */
val FittedTextPadding = 6.dp

/** Smallest size (sp) chip labels shrink to when they're too wide for their chips. */
private const val MIN_CHIP_FONT_SIZE = 8f

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
 * One label style shared by all chips in a set: as large as a chip [chipHeight] tall allows
 * (see [boxFittedTextStyle]), shrunk until the widest of [labels] fits a chip [chipWidth] wide
 * minus [FittedTextPadding] at start and end — so every label has the same size. Tablets only
 * ([Dimens.fitTextToBoxes]); phone: the default text style.
 *
 * [labels] should cover everything a chip can show (e.g. a worst-case pitch value), not just the
 * current texts, so the size doesn't jump while the values change.
 */
@Composable
fun rememberChipLabelStyle(chipWidth: Dp, chipHeight: Dp, labels: List<String>): TextStyle {
    val base = boxFittedTextStyle(chipHeight)
    if (!LocalDimens.current.fitTextToBoxes) return base
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(base, chipWidth, labels, density) {
        val availablePx = with(density) { (chipWidth - FittedTextPadding * 2).toPx() }
        // Text width scales linearly with the font size: measure once at the max size.
        val widestPx = labels.maxOfOrNull { label ->
            measurer.measure(label.trim(), base, maxLines = 1, softWrap = false).size.width
        } ?: 0
        val maxSize = base.fontSize.value
        val fitted = if (widestPx > 0) minOf(maxSize, maxSize * availablePx / widestPx) else maxSize
        // Floor to 0.5 sp so rounding can't push the widest label past the padding.
        base.copy(fontSize = maxOf(floor(fitted * 2f) / 2f, MIN_CHIP_FONT_SIZE).sp)
    }
}

/**
 * A single-line chip label in [style] (from [rememberChipLabelStyle]). Tablets: centered, with
 * [FittedTextPadding] at start and end; the text is trimmed (the pitch value carries leading
 * spaces as a phone-layout alignment hack). Phone: plain [Text].
 */
@Composable
fun ChipLabel(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    if (!LocalDimens.current.fitTextToBoxes) {
        Text(text = text, color = color, style = style, modifier = modifier)
        return
    }
    Text(
        text = text.trim(),
        color = color,
        style = style,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.padding(horizontal = FittedTextPadding)
    )
}
