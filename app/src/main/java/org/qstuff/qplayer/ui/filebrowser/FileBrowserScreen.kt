package org.qstuff.qplayer.ui.filebrowser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.ui.playlists.M3uUtils
import org.qstuff.qplayer.ui.queue.QueueViewModel
import org.qstuff.qplayer.ui.player.PlayerViewModel
import org.qstuff.qplayer.util.directoryContainsSupportedFiles
import org.qstuff.qplayer.util.isM3UList
import org.qstuff.qplayer.util.listTracksForAddDialog
import java.io.File
import java.io.FileInputStream

private val QOrangeDivider = Color(0x60FC7614)

@Composable
fun FileBrowserScreen(
    fileBrowserViewModel: FileBrowserViewModel,
    queueViewModel: QueueViewModel,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current

    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(storagePermission)
    }

    if (!hasPermission) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Please grant storage permission to browse files",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }

    val files by fileBrowserViewModel.fileList.observeAsState(emptyList())
    val directoryName by fileBrowserViewModel.directoryName.observeAsState("")

    var addTracksDialogFiles by remember { mutableStateOf<List<File>?>(null) }
    var m3uDialogFile by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Text(
            text = directoryName,
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
                onClick = { fileBrowserViewModel.navigateUp() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.SubdirectoryArrowLeft,
                    contentDescription = "Navigate up",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(color = QOrangeDivider, thickness = 1.dp)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(files, key = { it.absolutePath }) { file ->
                FileListItem(
                    file = file,
                    onClick = {
                        when {
                            file.isM3UList() -> m3uDialogFile = file
                            file.isFile -> queueViewModel.addFile(file)
                            file.isDirectory -> fileBrowserViewModel.onFileItemClicked(file)
                        }
                    },
                    onLongClick = {
                        if (file.isDirectory && file.directoryContainsSupportedFiles()) {
                            addTracksDialogFiles = file.listTracksForAddDialog()
                        }
                    },
                    onPrelistenClick = {
                        playerViewModel.loadTrack(Track(file, true))
                    }
                )
                HorizontalDivider(color = QOrangeDivider, thickness = 1.dp)
            }
        }
    }

    addTracksDialogFiles?.let { filesToAdd ->
        AlertDialog(
            onDismissRequest = { addTracksDialogFiles = null },
            title = { Text(stringResource(R.string.filebrowser_dialog_add_tracks_to_queue_title)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(filesToAdd) { file ->
                        Text(
                            text = file.name,
                            modifier = Modifier.padding(vertical = 4.dp),
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    queueViewModel.addFileList(filesToAdd)
                    addTracksDialogFiles = null
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        queueViewModel.clearTrackList()
                        queueViewModel.addFileList(filesToAdd)
                        addTracksDialogFiles = null
                    }) { Text(stringResource(R.string.filebrowser_dialog_queue_overwrite)) }
                    TextButton(onClick = { addTracksDialogFiles = null }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            }
        )
    }

    m3uDialogFile?.let { file ->
        val parsed = remember(file) {
            M3uUtils.m3UParserGetTracks(FileInputStream(file), file.parent ?: "")
        }
        val found = parsed.first
        val notFound = parsed.second

        AlertDialog(
            onDismissRequest = { m3uDialogFile = null },
            title = {
                Text(
                    if (found.isEmpty())
                        stringResource(R.string.add_m3ulist_to_queue_dialog_no_tracks_found_title, file.name)
                    else
                        stringResource(R.string.add_m3ulist_to_queue_dialog_tracks_found_title, file.name)
                )
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    if (found.isNotEmpty()) {
                        item { Text("Found:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        items(found) { track ->
                            Text(track.name, fontSize = 15.sp, color = Color.White, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                    if (notFound.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)) }
                        item { Text("Not found:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
                        items(notFound) { track ->
                            Text(track.name, fontSize = 15.sp, color = Color(0xFF999999), modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            },
            confirmButton = {
                if (found.isNotEmpty()) {
                    TextButton(onClick = {
                        queueViewModel.addTrackList(found)
                        m3uDialogFile = null
                    }) { Text(stringResource(R.string.dialog_ok)) }
                } else {
                    TextButton(onClick = { m3uDialogFile = null }) { Text(stringResource(R.string.dialog_ok)) }
                }
            },
            dismissButton = {
                if (found.isNotEmpty()) {
                    Row {
                        TextButton(onClick = {
                            queueViewModel.clearTrackList()
                            queueViewModel.addTrackList(found)
                            m3uDialogFile = null
                        }) { Text(stringResource(R.string.filebrowser_dialog_queue_overwrite)) }
                        TextButton(onClick = { m3uDialogFile = null }) {
                            Text(stringResource(R.string.dialog_cancel))
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    file: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPrelistenClick: () -> Unit
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
        val icon = when {
            file.isDirectory -> Icons.Default.Folder
            file.isM3UList() -> Icons.Default.PlaylistPlay
            else -> Icons.Default.MusicNote
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = file.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (file.isFile && !file.isM3UList()) {
            IconButton(
                onClick = onPrelistenClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Pre-listen",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
