package org.qstuff.qplayer.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import org.qstuff.qplayer.R
import org.qstuff.qplayer.ui.theme.QOrange

/**
 * Track title (marquee when it overflows) plus the duration row (total on the left, the
 * remaining/current time on the right — tap it to toggle, [onToggleTimeDisplay]).
 */
@Composable
fun TrackInfoSection(
    trackTitleText: String,
    totalDurationText: String,
    dynamicTimeText: String,
    dynamicTimeAlpha: Float,
    onToggleTimeDisplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roundedShape = RoundedCornerShape(dimensionResource(R.dimen.rounded_shape_radius))

    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.textview_height))
                .background(Color.Black, roundedShape)
                .padding(
                    start = dimensionResource(R.dimen.textview_padding_start),
                    top = 4.dp
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            MarqueeText(
                text = trackTitleText,
                color = QOrange,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.textview_height))
                .background(Color.Black, roundedShape),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = totalDurationText,
                color = Color.White,
                modifier = Modifier.padding(start = dimensionResource(R.dimen.textview_padding_start))
            )
            Text(
                text = dynamicTimeText,
                color = QOrange,
                modifier = Modifier
                    .padding(end = dimensionResource(R.dimen.textview_padding_end))
                    .graphicsLayer { alpha = dynamicTimeAlpha }
                    .clickable { onToggleTimeDisplay() }
            )
        }
    }
}

/**
 * Single-line text that, when it's wider than the available space, animates back and forth:
 * scrolls to the end, pauses, scrolls back to the start, pauses, repeat. Text that fits is
 * left static.
 */
@Composable
private fun MarqueeText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    startDelayMillis: Long = 1500,
    edgeDelayMillis: Long = 1200,
    velocityPxPerSec: Float = 45f
) {
    val offsetX = remember { Animatable(0f) }
    var containerWidth by remember { mutableStateOf(0) }
    var textWidth by remember { mutableStateOf(0) }

    LaunchedEffect(text, textWidth, containerWidth) {
        val overflow = textWidth - containerWidth
        if (overflow <= 0) {
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }
        val duration = (overflow / velocityPxPerSec * 1000f).toInt().coerceAtLeast(1)
        offsetX.snapTo(0f)
        delay(startDelayMillis)
        while (isActive) {
            offsetX.animateTo(-overflow.toFloat(), tween(duration, easing = LinearEasing))
            delay(edgeDelayMillis)
            offsetX.animateTo(0f, tween(duration, easing = LinearEasing))
            delay(edgeDelayMillis)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .onSizeChanged { textWidth = it.width }
        )
    }
}
