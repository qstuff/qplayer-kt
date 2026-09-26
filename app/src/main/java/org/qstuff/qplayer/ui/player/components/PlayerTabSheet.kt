package org.qstuff.qplayer.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.qstuff.qplayer.R
import org.qstuff.qplayer.ui.filebrowser.FileBrowserScreen
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.player.PlayerViewModel
import org.qstuff.qplayer.ui.playlists.PlaylistScreen
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueScreen
import org.qstuff.qplayer.ui.queue.QueueViewModel
import org.qstuff.qplayer.ui.theme.QOrange

/**
 * The draggable bottom sheet with the Queue / Files / Playlists tabs and pager.
 *
 * Its height is driven in the layout phase from an [Animatable] offset (0 = expanded to
 * [expandedHeightPx], [maxSheetOffsetPx] = collapsed) so dragging only re-lays-out and the tab
 * lists' viewports track the visible height. Drag is bound to the notch handle and the TabRow.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabTitles = listOf(
        stringResource(R.string.queue_title),
        stringResource(R.string.filebrowser_title),
        stringResource(R.string.playlists_title)
    )
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
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Black,
            contentColor = QOrange,
            modifier = sheetDragModifier
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        if (pagerState.currentPage == index) {
                            // tap on the already-selected tab → toggle the sheet
                            val target =
                                if (sheetOffset.value < maxSheetOffsetPx / 2) maxSheetOffsetPx
                                else 0f
                            sheetScope.launch { sheetOffset.animateTo(target, tween(300)) }
                        } else {
                            sheetScope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                    text = { Text(title) },
                    selectedContentColor = QOrange,
                    unselectedContentColor = Color.White
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondBoundsPageCount = 1
        ) { page ->
            when (page) {
                0 -> QueueScreen(
                    queueViewModel = queueViewModel,
                    playlistViewModel = playlistViewModel
                )
                1 -> FileBrowserScreen(
                    fileBrowserViewModel = fileBrowserViewModel,
                    queueViewModel = queueViewModel,
                    playerViewModel = playerViewModel
                )
                2 -> PlaylistScreen(
                    playlistViewModel = playlistViewModel,
                    queueViewModel = queueViewModel
                )
            }
        }
    }
}
