package org.qstuff.qplayer.ui.player.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
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
 * The Queue / Files / Playlists tabs and their pager. Used inside the phone's draggable
 * [PlayerTabSheet] and as the permanent right pane of the tablet layout.
 *
 * @param tabRowModifier extra modifier for the TabRow (the sheet makes it a drag handle).
 * @param onSelectedTabClick tap on the already-selected tab (the sheet toggles itself).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerTabs(
    queueViewModel: QueueViewModel,
    playlistViewModel: PlaylistViewModel,
    fileBrowserViewModel: FileBrowserViewModel,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    tabRowModifier: Modifier = Modifier,
    onSelectedTabClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabTitles = listOf(
        stringResource(R.string.queue_title),
        stringResource(R.string.filebrowser_title),
        stringResource(R.string.playlists_title)
    )

    Column(modifier = modifier) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Black,
            contentColor = QOrange,
            modifier = tabRowModifier
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        if (pagerState.currentPage == index) {
                            onSelectedTabClick()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(index) }
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
            beyondViewportPageCount = 1
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
