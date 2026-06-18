package org.qstuff.qplayer.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.queue.QueueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistViewModel: PlaylistViewModel,
    queueViewModel: QueueViewModel
) {
    val playlists by playlistViewModel.playlistList.observeAsState(emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var openPlaylist by remember { mutableStateOf<Playlist?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(playlists, key = { it.playlist_id }) { playlist ->
                val index = playlists.indexOf(playlist)
                SwipeToDismissPlaylist(
                    onDismissed = {
                        playlistViewModel.removePlaylist(playlist)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = playlist.name,
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                playlistViewModel.restorePlaylistAt(playlist, index)
                            }
                        }
                    }
                ) {
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { openPlaylist = playlist }
                    )
                }
                HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 0.5.dp)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    openPlaylist?.let { playlist ->
        val tracks = remember(playlist) { playlistViewModel.getTracksForPlaylist(playlist) }
        OpenPlaylistDialog(
            playlist = playlist,
            tracks = tracks,
            onAddToQueue = {
                queueViewModel.addTrackList(tracks)
                openPlaylist = null
            },
            onReplaceQueue = {
                queueViewModel.replaceTrackList(tracks)
                openPlaylist = null
            },
            onDismiss = { openPlaylist = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissPlaylist(
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
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = playlist.name,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OpenPlaylistDialog(
    playlist: Playlist,
    tracks: List<Track>,
    onAddToQueue: () -> Unit,
    onReplaceQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(playlist.name) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(tracks) { track ->
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddToQueue) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReplaceQueue) {
                    Text(stringResource(R.string.filebrowser_dialog_queue_overwrite))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        }
    )
}
