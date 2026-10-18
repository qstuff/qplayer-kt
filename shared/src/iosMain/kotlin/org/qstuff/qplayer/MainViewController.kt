package org.qstuff.qplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.di.iosModules
import org.qstuff.qplayer.platform.IosBuild
import org.qstuff.qplayer.platform.IosFileImport
import org.qstuff.qplayer.ui.QDeqApp
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.player.PlaybackCoordinator
import org.qstuff.qplayer.ui.player.PlayerViewModel
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueViewModel
import platform.UIKit.UIViewController

/**
 * The iOS app's UI (called from Swift, see iosApp/iosApp/ContentView.swift): the shared screens
 * with the same wiring as Android's PlayerActivity.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        startKoin { modules(iosModules) }
    }
    return ComposeUIViewController {
        val playerViewModel: PlayerViewModel = koinViewModel()
        val queueViewModel: QueueViewModel = koinViewModel()
        val playlistViewModel: PlaylistViewModel = koinViewModel()
        val fileBrowserViewModel: FileBrowserViewModel = koinViewModel()
        val preferencesDataSource: PreferencesDataSource = koinInject()
        val coordinator = remember {
            PlaybackCoordinator(
                playerViewModel, queueViewModel, playlistViewModel, fileBrowserViewModel, preferencesDataSource
            )
        }

        LaunchedEffect(Unit) {
            playerViewModel.connectPlayer()
            coordinator.start(this)
        }
        // Moving to the background: keep queue, position and settings, like Android's onStop.
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { coordinator.saveState() }
        // Back in the foreground: files may have been added meanwhile (Files app, Finder).
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { fileBrowserViewModel.refresh() }
        // Files handed over by other apps: show the folder they were copied to.
        LaunchedEffect(Unit) {
            IosFileImport.importedTo.collect { fileBrowserViewModel.openFolder(it) }
        }

        // Full screen (see ContentView.swift), but the app stays clear of status bar and home
        // indicator (black there), like on Android, where it doesn't draw behind the system bars.
        Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
            QDeqApp(
                playerViewModel = playerViewModel,
                queueViewModel = queueViewModel,
                playlistViewModel = playlistViewModel,
                fileBrowserViewModel = fileBrowserViewModel,
                titleSuffix = IosBuild.titleSuffix,
                askForCrashReporting = false    // no crash reporting on iOS yet
            )
        }
    }
}
