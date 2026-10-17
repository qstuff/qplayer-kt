package org.qstuff.qplayer.ui.filebrowser

import androidx.compose.runtime.Composable

/**
 * Shows [content] once the app may read the music files, asking for the permission first where
 * the platform needs one (Android; iOS needs none for the app's own folders).
 */
@Composable
expect fun StoragePermissionGate(content: @Composable () -> Unit)
