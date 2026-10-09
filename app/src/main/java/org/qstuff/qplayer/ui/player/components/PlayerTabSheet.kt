package org.qstuff.qplayer.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.player.PlayerViewModel
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueViewModel

/**
 * The draggable bottom sheet (phone layout) holding the [PlayerTabs].
 *
 * Its height is driven in the layout phase from an [Animatable] offset (0 = expanded to
 * [expandedHeightPx], [maxSheetOffsetPx] = collapsed) so dragging only re-lays-out and the tab
 * lists' viewports track the visible height. Drag is bound to the notch handle and the TabRow.
 */
@Composable
fun PlayerTabSheet(
    expandedHeightPx: Float,
    maxSheetOffsetPx: Float,
    queueViewModel: QueueViewModel,
    playlistViewModel: PlaylistViewModel,
    fileBrowserViewModel: FileBrowserViewModel,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val sheetOffset = remember { Animatable(maxSheetOffsetPx) }
    val sheetScope = rememberCoroutineScope()
    // Shared drag behavior for the collapse/expand handle areas (notch row + TabRow).
    val sheetDragModifier = Modifier.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            sheetScope.launch {
                sheetOffset.snapTo(
                    (sheetOffset.value + delta).coerceIn(0f, maxSheetOffsetPx)
                )
            }
        },
        onDragStopped = { velocity ->
            val target = when {
                velocity > 800f -> maxSheetOffsetPx   // fling down → collapse
                velocity < -800f -> 0f                // fling up → expand
                sheetOffset.value > maxSheetOffsetPx / 2 -> maxSheetOffsetPx
                else -> 0f
            }
            sheetScope.launch { sheetOffset.animateTo(target, tween(300)) }
        }
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Height = expandedHeight - sheetOffset, forced in the layout phase so the drag only
            // re-lays-out (no recomposition). Reading sheetOffset here makes the list viewport
            // track the visible height → short lists scroll too.
            .layout { measurable, constraints ->
                val h = (expandedHeightPx - sheetOffset.value).roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
            .background(Color.Black)
            // Block taps from reaching the content behind the sheet when it's expanded.
            .pointerInput(Unit) {}
    ) {
        // Grab handle: a white rounded bar, ~half a tab wide, at the very top of the panel.
        // The full-width row is draggable to collapse/expand the sheet.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(sheetDragModifier)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f / 6f)
                    .height(6.dp)
                    .background(Color.White, RoundedCornerShape(3.dp))
            )
        }
        PlayerTabs(
            queueViewModel = queueViewModel,
            playlistViewModel = playlistViewModel,
            fileBrowserViewModel = fileBrowserViewModel,
            playerViewModel = playerViewModel,
            modifier = Modifier.weight(1f),
            tabRowModifier = sheetDragModifier,
            // tap on the already-selected tab → toggle the sheet
            onSelectedTabClick = {
                val target =
                    if (sheetOffset.value < maxSheetOffsetPx / 2) maxSheetOffsetPx else 0f
                sheetScope.launch { sheetOffset.animateTo(target, tween(300)) }
            }
        )
    }
}
