package org.qstuff.qplayer.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.ui.queue.QueueViewModel
import org.qstuff.qplayer.ui.SwipeToRemove
import org.jetbrains.compose.resources.stringResource
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.dialog_cancel
import org.qstuff.qplayer.resources.dialog_ok
import org.qstuff.qplayer.resources.filebrowser_dialog_queue_overwrite
import org.qstuff.qplayer.resources.snackbar_undo

private val QOrangeDivider = Color(0x60FC7614)

/**
 * The Playlists tab: the list of playlists — a tap opens one (its tracks, which can be swiped
 * away), a long press offers to add it to the queue or replace the queue with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistViewModel: PlaylistViewModel,
    queueViewModel: QueueViewModel
) {
    val playlists by playlistViewModel.playlistList.collectAsStateWithLifecycle()
    val openedPlaylist by playlistViewModel.openedPlaylist.collectAsStateWithLifecycle()
    val openedTracks by playlistViewModel.openedTracks.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(Res.string.snackbar_undo)
    val scope = rememberCoroutineScope()

    // Long press: the add-to / replace-queue dialog for this playlist.
    var dialogPlaylist by remember { mutableStateOf<Playlist?>(null) }

    fun undoable(message: String, onUndo: () -> Unit) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
        val opened = openedPlaylist
        if (opened == null) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists, key = { it.playlist_id }) { playlist ->
                    val index = playlists.indexOf(playlist)
                    SwipeToRemove(
                        onDismissed = {
                            playlistViewModel.removePlaylist(playlist)
                            undoable(playlist.name) { playlistViewModel.restorePlaylistAt(playlist, index) }
                        }
                    ) {
                        PlaylistItem(
                            playlist = playlist,
                            onClick = { playlistViewModel.openPlaylist(playlist) },
                            onLongClick = { dialogPlaylist = playlist }
                        )
                    }
                    HorizontalDivider(color = QOrangeDivider, thickness = 1.dp)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = opened.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { playlistViewModel.closePlaylist() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.SubdirectoryArrowLeft,
                            contentDescription = "Back to the playlists",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                HorizontalDivider(color = QOrangeDivider, thickness = 1.dp)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(openedTracks, key = { it.track_id }) { track ->
                        val index = openedTracks.indexOf(track)
                        SwipeToRemove(
                            onDismissed = {
                                playlistViewModel.removeTrack(track)
                                undoable(track.name) { playlistViewModel.restoreTrack(track, index) }
                            }
                        ) {
                            PlaylistTrackItem(track)
                        }
                        HorizontalDivider(color = QOrangeDivider, thickness = 1.dp)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    dialogPlaylist?.let { playlist ->
        // Loaded off the main thread; the dialog appears once the tracks are there.
        val tracks = produceState<List<Track>?>(initialValue = null, playlist) {
            value = playlistViewModel.getTracksForPlaylist(playlist)
        }.value ?: return@let
        OpenPlaylistDialog(
            playlist = playlist,
            tracks = tracks,
            onAddToQueue = {
                queueViewModel.addTrackList(tracks)
                dialogPlaylist = null
            },
            onReplaceQueue = {
                queueViewModel.replaceTrackList(tracks)
                dialogPlaylist = null
            },
            onDismiss = { dialogPlaylist = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Color.Black)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LibraryMusic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistTrackItem(track: Track) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Color.Black)
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
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
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
                        fontSize = 15.sp,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    HorizontalDivider(color = QOrangeDivider, thickness = 1.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddToQueue) {
                Text(stringResource(Res.string.dialog_ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReplaceQueue) {
                    Text(stringResource(Res.string.filebrowser_dialog_queue_overwrite))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            }
        }
    )
}
