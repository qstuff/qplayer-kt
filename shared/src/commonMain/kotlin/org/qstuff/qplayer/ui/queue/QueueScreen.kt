package org.qstuff.qplayer.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.SwipeToRemove
import org.qstuff.qplayer.ui.theme.QOrange
import org.jetbrains.compose.resources.stringResource
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.dialog_cancel
import org.qstuff.qplayer.resources.dialog_ok
import org.qstuff.qplayer.resources.queue_dialog_add_tracks_to_existing_playlist_append
import org.qstuff.qplayer.resources.queue_dialog_add_tracks_to_existing_playlist_message
import org.qstuff.qplayer.resources.queue_dialog_add_tracks_to_existing_playlist_overwrite
import org.qstuff.qplayer.resources.queue_dialog_add_tracks_to_existing_playlist_title
import org.qstuff.qplayer.resources.queue_dialog_confirm_clear_message
import org.qstuff.qplayer.resources.queue_dialog_confirm_clear_title
import org.qstuff.qplayer.resources.queue_dialog_save_tracks_as_playlist_message
import org.qstuff.qplayer.resources.queue_dialog_save_tracks_as_playlist_title
import org.qstuff.qplayer.resources.snackbar_undo
import androidx.compose.foundation.layout.Column
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

private val QOrangeDivider = Color(0x60FC7614)
/** A track lifted by long press to be moved. */
private val DraggedBackground = Color(0xFF6B3500)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    queueViewModel: QueueViewModel,
    playlistViewModel: PlaylistViewModel
) {
    // toList() snapshots the ArrayList — the ViewModel mutates the same object in place,
    // which would crash LazyColumn's getContentType if we captured the mutable reference.
    val tracks = queueViewModel.trackList.collectAsStateWithLifecycle().value.toList()
    val selectedIndex by queueViewModel.onTrackSelectedIndex.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlistList.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(Res.string.snackbar_undo)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }

    // The highlighted track, by file: stays highlighted while tracks are moved.
    val selectedUri = tracks.getOrNull(selectedIndex)?.uri

    // Scroll to a newly selected track — not when the selection only moved in the list.
    LaunchedEffect(selectedUri) {
        if (selectedIndex in 0 until tracks.size) listState.animateScrollToItem(selectedIndex)
    }

    // Long press + drag reorders: this copy follows the finger, the queue gets it on drop.
    var orderedTracks by remember(tracks) { mutableStateOf(tracks) }
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        orderedTracks = orderedTracks.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = {
                        playlistViewModel.loadPlaylists()
                        showSavePlaylistDialog = true
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Save as playlist", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        if (queueViewModel.isShowClearQueueWarningEnabled) showClearDialog = true
                        else queueViewModel.clearTrackList()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.ClearAll, contentDescription = "Clear queue", tint = Color.White)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(orderedTracks, key = { track -> track.uri }) { track ->
                    ReorderableItem(reorderState, key = track.uri) { isDragging ->
                        Column {
                            SwipeToRemove(
                                onDismissed = {
                                    val index = tracks.indexOf(track)
                                    queueViewModel.removeTrack(track)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = track.name,
                                            actionLabel = undoLabel,
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
                                    isSelected = track.uri == selectedUri,
                                    isDragging = isDragging,
                                    onClick = { queueViewModel.onTrackSelected(track) },
                                    modifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = { queueViewModel.reorderTracks(orderedTracks) }
                                    )
                                )
                            }
                            HorizontalDivider(color = QOrangeDivider, thickness = 1.dp)
                        }
                    }
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
            title = { Text(stringResource(Res.string.queue_dialog_confirm_clear_title)) },
            text = { Text(stringResource(Res.string.queue_dialog_confirm_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    queueViewModel.clearTrackList()
                    showClearDialog = false
                }) { Text(stringResource(Res.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(Res.string.dialog_cancel))
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

@Composable
private fun TrackListItem(
    track: Track,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    val bgColor = when {
        isDragging -> DraggedBackground
        isSelected -> Color(0xFF3A1E00)
        else -> Color.Black
    }
    val textColor = if (isSelected) QOrange else Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = track.name,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
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
            title = { Text(stringResource(Res.string.queue_dialog_add_tracks_to_existing_playlist_title)) },
            text = { Text(stringResource(Res.string.queue_dialog_add_tracks_to_existing_playlist_message)) },
            confirmButton = {
                TextButton(onClick = { onOverwriteExisting(selectedPlaylist!!) }) {
                    Text(stringResource(Res.string.queue_dialog_add_tracks_to_existing_playlist_overwrite))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onAppendToExisting(selectedPlaylist!!) }) {
                        Text(stringResource(Res.string.queue_dialog_add_tracks_to_existing_playlist_append))
                    }
                    TextButton(onClick = { selectedPlaylist = null }) {
                        Text(stringResource(Res.string.dialog_cancel))
                    }
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.queue_dialog_save_tracks_as_playlist_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.queue_dialog_save_tracks_as_playlist_message))
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        HorizontalDivider(color = QOrangeDivider, thickness = 1.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (newName.isNotBlank()) onSaveAsNew(newName) },
                enabled = newName.isNotBlank()
            ) { Text(stringResource(Res.string.dialog_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
        }
    )
}
