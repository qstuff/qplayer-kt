package org.qstuff.qplayer.ui.player.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import org.qstuff.qplayer.R
import org.qstuff.qplayer.ui.theme.QOrange

/**
 * The shared player control chip: a full-height black rounded box that takes an equal share of
 * its Row (weight 1) and centers its content. Optionally clickable / long-clickable.
 *
 * Declared as a [RowScope] extension so it can apply `weight(1f)` itself — callers just drop it
 * into a Row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.ControlChip(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val roundedShape = RoundedCornerShape(dimensionResource(R.dimen.rounded_shape_radius))
    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .weight(1f)
            .height(dimensionResource(R.dimen.textview_height))
            .background(Color.Black, roundedShape)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Icon variant of [ControlChip]. */
@Composable
fun RowScope.IconControlChip(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = QOrange
) = ControlChip(onClick = onClick) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        tint = tint
    )
}

/** Text variant of [ControlChip]. Pass a null [onClick] for a non-interactive chip. */
@Composable
fun RowScope.TextControlChip(
    text: String,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    color: Color = Color.White
) = ControlChip(onClick = onClick, onLongClick = onLongClick) {
    Text(text = text, color = color)
}
