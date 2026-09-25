package org.qstuff.qplayer.queue

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
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.SwipeToRemove
import org.qstuff.qplayer.ui.theme.QOrange

private val QOrangeDivider = Color(0x60FC7614)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    queueViewModel: QueueViewModel,
    playlistViewModel: PlaylistViewModel
) {
    // toList() snapshots the ArrayList — the ViewModel mutates the same object in place,
    // which would crash LazyColumn's getContentType if we captured the mutable reference.
    val tracks = queueViewModel.trackList.observeAsState(emptyList()).value.toList()
    val selectedIndex by queueViewModel.onTrackSelectedIndex.observeAsState()
    val playlists by playlistViewModel.playlistList.observeAsState(emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex) {
        selectedIndex?.let {
            if (it >= 0 && it < tracks.size) listState.animateScrollToItem(it)
        }
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
                    Icon(Icons.Default.PlaylistAdd, contentDescription = "Save as playlist", tint = Color.White)
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
                items(tracks, key = { track -> track.uri }) { track ->
                    val index = tracks.indexOf(track)
                    val isSelected = index == selectedIndex
                    SwipeToRemove(
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
                    HorizontalDivider(color = QOrangeDivider, thickness = 1.dp)
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

@Composable
private fun TrackListItem(
    track: Track,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF3A1E00) else Color.Black
    val textColor = if (isSelected) QOrange else Color.White

    Row(
        modifier = Modifier
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
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
