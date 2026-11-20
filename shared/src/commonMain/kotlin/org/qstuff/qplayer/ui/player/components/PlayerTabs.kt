package org.qstuff.qplayer.ui.player.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.qstuff.qplayer.ui.filebrowser.FileBrowserScreen
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.player.PlayerViewModel
import org.qstuff.qplayer.ui.playlists.PlaylistScreen
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueScreen
import org.qstuff.qplayer.ui.queue.QueueViewModel
import org.qstuff.qplayer.ui.theme.QOrange
import org.qstuff.qplayer.ui.theme.fittedTextStyle
import org.qstuff.qplayer.ui.theme.rememberWidthFittedStyle
import org.jetbrains.compose.resources.stringResource
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.filebrowser_title
import org.qstuff.qplayer.resources.playlists_title
import org.qstuff.qplayer.resources.queue_title

/** Material 3's text-only Tab height. */
private val TAB_HEIGHT = 48.dp

/** Gap between a tab label and the tab's top/bottom edge. */
private val TAB_LABEL_PADDING = 8.dp

/** Material 3 Tab's own start/end content padding. */
private val TAB_HORIZONTAL_PADDING = 16.dp

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
    folderTabs: Boolean = false,
    onSelectedTabClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabTitles = listOf(
        stringResource(Res.string.queue_title),
        stringResource(Res.string.filebrowser_title),
        stringResource(Res.string.playlists_title)
    )

    Column(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Tab labels fill the tab height (minus TAB_LABEL_PADDING top/bottom) and share one
            // size, shrunk so the longest title fits one tab (minus the Tab's start/end padding).
            val tabLabelStyle = rememberWidthFittedStyle(
                base = fittedTextStyle(TAB_HEIGHT, TAB_LABEL_PADDING),
                availableWidth = maxWidth / tabTitles.size - TAB_HORIZONTAL_PADDING * 2,
                labels = tabTitles
            )
            val onTabClick: (Int) -> Unit = { index ->
                if (pagerState.currentPage == index) {
                    onSelectedTabClick()
                } else {
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            }
            if (folderTabs) {
                FolderTabRow(
                    titles = tabTitles,
                    selectedIndex = pagerState.currentPage,
                    labelStyle = tabLabelStyle,
                    onTabClick = onTabClick,
                    modifier = tabRowModifier
                )
            } else {
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
                            text = {
                                Text(title, style = tabLabelStyle, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.height(TAB_HEIGHT),
                            selectedContentColor = QOrange,
                            unselectedContentColor = Color.White
                        )
                    }
                }
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

/**
 * Classic-layout tabs, like folder tabs: black boxes below the sheet's black handle row, with
 * white gaps between them. The selected one runs into the black list below; the others end above a
 * white line. Selected label orange, the others white — no indicator.
 */
@Composable
private fun FolderTabRow(
    titles: List<String>,
    selectedIndex: Int,
    labelStyle: TextStyle,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TAB_HEIGHT)
            .background(Color.White),
        horizontalArrangement = Arrangement.spacedBy(FOLDER_TAB_GAP)
    ) {
        titles.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(bottom = if (selected) 0.dp else FOLDER_TAB_GAP)
                    .background(Color.Black)
                    .clickable { onTabClick(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    style = labelStyle,
                    color = if (selected) QOrange else Color.White,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/** White gap between folder tabs and below the unselected ones. */
private val FOLDER_TAB_GAP = 4.dp
