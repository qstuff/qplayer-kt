package org.qstuff.qplayer.ui.player.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.qstuff.qplayer.ui.theme.ChipLabel
import org.qstuff.qplayer.ui.theme.LocalDimens

/**
 * The shared player control chip: a black rounded box that centers its content, optionally
 * clickable / long-clickable. Its size comes from [modifier] — see the [RowScope] variants for
 * the equal-share button rows, the classic layout places chips with explicit sizes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChipBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val roundedShape = RoundedCornerShape(LocalDimens.current.roundedShapeRadius)
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
            .background(Color.Black, roundedShape)
            .then(clickModifier),
        contentAlignment = contentAlignment,
        content = content
    )
}

/** A [ChipBox] taking an equal share of its Row (weight 1), one button row tall. */
@Composable
fun RowScope.ControlChip(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) = ChipBox(
    modifier = modifier.weight(1f).height(LocalDimens.current.textviewHeight),
    onClick = onClick,
    onLongClick = onLongClick,
    content = content
)

/**
 * Icon chip. By default the icon keeps the drawable's own colors ([Color.Unspecified]) — pass an
 * explicit [tint] only for single-color drawables that need coloring. (A blanket tint would
 * silently override state-encoding drawables like the repeat/shuffle icons, where white = off and
 * orange = on.) [iconSize] unspecified: the icon's own size.
 */
@Composable
fun IconChip(
    iconRes: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    iconSize: Dp = Dp.Unspecified
) = ChipBox(modifier = modifier, onClick = onClick) {
    ChipIcon(iconRes, contentDescription, tint, iconSize)
}

/** The icon of an [IconChip], for chips with their own layout. */
@Composable
fun ChipIcon(
    iconRes: DrawableResource,
    contentDescription: String,
    tint: Color = Color.Unspecified,
    iconSize: Dp = Dp.Unspecified
) = Icon(
    painter = painterResource(iconRes),
    contentDescription = contentDescription,
    tint = tint,
    modifier = if (iconSize.isSpecified) Modifier.size(iconSize) else Modifier
)

/** [IconChip] taking an equal share of its Row, one button row tall. */
@Composable
fun RowScope.IconControlChip(
    iconRes: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
    iconSize: Dp = Dp.Unspecified
) = IconChip(
    iconRes, contentDescription, onClick,
    modifier = Modifier.weight(1f).height(LocalDimens.current.textviewHeight),
    tint = tint,
    iconSize = iconSize
)

/**
 * Text chip. Pass a null [onClick] for a non-interactive chip. [labelStyle] comes from
 * rememberChipLabelStyle, so all labels of a button bar share one size.
 */
@Composable
fun TextChip(
    text: String,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    color: Color = Color.White
) = ChipBox(modifier = modifier, onClick = onClick, onLongClick = onLongClick) {
    ChipLabel(text = text, color = color, style = labelStyle)
}

/** [TextChip] taking an equal share of its Row, one button row tall. */
@Composable
fun RowScope.TextControlChip(
    text: String,
    labelStyle: TextStyle,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    color: Color = Color.White
) = TextChip(
    text, labelStyle,
    modifier = Modifier.weight(1f).height(LocalDimens.current.textviewHeight),
    onClick = onClick,
    onLongClick = onLongClick,
    color = color
)
