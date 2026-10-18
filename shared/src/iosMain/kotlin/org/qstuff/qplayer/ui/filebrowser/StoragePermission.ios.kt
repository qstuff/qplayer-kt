package org.qstuff.qplayer.ui.filebrowser

import androidx.compose.runtime.Composable

// The app's own folders need no permission on iOS.
@Composable
actual fun StoragePermissionGate(content: @Composable () -> Unit) = content()
