package org.qstuff.qplayer.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Swipe-an-item-left-to-remove, built on a horizontal [draggable].
 *
 * Unlike Material3's `SwipeToDismissBox`, whose horizontal `anchoredDraggable` grabs the
 * pointer on the initial (still-ambiguous) touch and thereby blocks the parent LazyColumn's
 * vertical scroll — a problem that bites when the list lives inside a HorizontalPager /
 * bottom sheet — `draggable(Orientation.Horizontal)` only engages once the gesture is
 * horizontally dominant, so vertical drags fall straight through to the list and scroll it.
 *
 * Only end-to-start (swipe left) is supported, matching the previous behaviour.
 */
@Composable
fun SwipeToRemove(
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat() }
    ) {
        // Red delete background, revealed as the content slides left.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFFE60C00))
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // clamp to left-only travel
                        offsetX = (offsetX + delta).coerceIn(-widthPx, 0f)
                    },
                    onDragStopped = {
                        val threshold = widthPx * 0.4f
                        if (widthPx > 0f && -offsetX > threshold) {
                            animate(offsetX, -widthPx, animationSpec = tween(200)) { v, _ -> offsetX = v }
                            onDismissed()
                        } else {
                            animate(offsetX, 0f, animationSpec = tween(200)) { v, _ -> offsetX = v }
                        }
                    }
                )
        ) {
            content()
        }
    }
}
