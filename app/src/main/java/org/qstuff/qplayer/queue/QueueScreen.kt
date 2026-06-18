package org.qstuff.qplayer.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.theme.QOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    queueViewModel: QueueViewModel,
    playlistViewModel: PlaylistViewModel
) {
    val tracks by queueViewModel.trackList.observeAsState(emptyList())
    val selectedIndex by queueViewModel.onTrackSelectedIndex.observeAsState()
    val playlists by playlistViewModel.playlistList.observeAsState(emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }

    // Scroll to selected track when it changes
    LaunchedEffect(selectedIndex) {
        selectedIndex?.let {
            if (it >= 0 && it < tracks.size) listState.animateScrollToItem(it)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = {
                    if (queueViewModel.isShowClearQueueWarningEnabled) showClearDialog = true
                    else queueViewModel.clearTrackList()
                }) {
                    Icon(
                        Icons.Default.ClearAll,
                        contentDescription = "Clear queue",
                        tint = Color.White
                    )
                }
                IconButton(onClick = {
                    playlistViewModel.loadPlaylists()
                    showSavePlaylistDialog = true
                }) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = "Save as playlist",
                        tint = Color.White
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(tracks, key = { _, track -> track.uri }) { index, track ->
                    val isSelected = index == selectedIndex
                    SwipeToRemoveItem(
                        onDismissed = {
                            queueViewModel.removeTrack(track)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = track.name,
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    queueViewModel.restoreTrackAt(track, index)
                                }
                            }
                        }
                    ) {
                        TrackListItem(
                            track = track,
                            isSelected = isSelected,
                            onClick = { queueViewModel.onTrackSelected(track) }
                        )
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 0.5.dp)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.queue_dialog_confirm_clear_title)) },
            text = { Text(stringResource(R.string.queue_dialog_confirm_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    queueViewModel.clearTrackList()
                    showClearDialog = false
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showSavePlaylistDialog) {
        SaveAsPlaylistDialog(
            playlists = playlists,
            onSaveAsNew = { name ->
                playlistViewModel.saveTracksAsNewPlaylist(tracks, name)
                showSavePlaylistDialog = false
            },
            onAppendToExisting = { playlist ->
                playlistViewModel.saveTracksToExistingPlaylist(tracks, playlist.name, false)
                showSavePlaylistDialog = false
            },
            onOverwriteExisting = { playlist ->
                playlistViewModel.saveTracksToExistingPlaylist(tracks, playlist.name, true)
                showSavePlaylistDialog = false
            },
            onDismiss = { showSavePlaylistDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToRemoveItem(
    onDismissed: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDismissed()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
            }
        }
    ) {
        content()
    }
}

@Composable
private fun TrackListItem(
    track: Track,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF2A1800) else Color.Transparent
    val textColor = if (isSelected) QOrange else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = track.name,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SaveAsPlaylistDialog(
    playlists: List<Playlist>,
    onSaveAsNew: (String) -> Unit,
    onAppendToExisting: (Playlist) -> Unit,
    onOverwriteExisting: (Playlist) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    if (selectedPlaylist != null) {
        AlertDialog(
            onDismissRequest = { selectedPlaylist = null },
            title = { Text(stringResource(R.string.queue_dialog_add_tracks_to_existing_playlist_title)) },
            text = { Text(stringResource(R.string.queue_dialog_add_tracks_to_existing_playlist_message)) },
            confirmButton = {
                TextButton(onClick = { onOverwriteExisting(selectedPlaylist!!) }) {
                    Text(stringResource(R.string.queue_dialog_add_tracks_to_existing_playlist_overwrite))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onAppendToExisting(selectedPlaylist!!) }) {
                        Text(stringResource(R.string.queue_dialog_add_tracks_to_existing_playlist_append))
                    }
                    TextButton(onClick = { selectedPlaylist = null }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.queue_dialog_save_tracks_as_playlist_title)) },
        text = {
            Column {
                Text(stringResource(R.string.queue_dialog_save_tracks_as_playlist_message))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (playlists.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Or add to existing:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    playlists.forEach { playlist ->
                        Text(
                            text = playlist.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPlaylist = playlist }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 0.5.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (newName.isNotBlank()) onSaveAsNew(newName) },
                enabled = newName.isNotBlank()
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
