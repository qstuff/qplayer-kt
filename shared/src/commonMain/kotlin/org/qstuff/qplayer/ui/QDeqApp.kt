package org.qstuff.qplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.player.PlayerViewModel
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueViewModel
import org.qstuff.qplayer.ui.settings.CrashReportingOptInDialog
import org.qstuff.qplayer.ui.theme.QDeqTheme
import org.qstuff.qplayer.util.CrashReporter

/**
 * The app's UI, hosted by the platform (Android: PlayerActivity, iOS: MainViewController): theme,
 * navigation and — where crash reporting exists ([askForCrashReporting]) — the opt-in dialog, asked
 * once and then changeable in Settings.
 */
@Composable
fun QDeqApp(
    playerViewModel: PlayerViewModel,
    queueViewModel: QueueViewModel,
    playlistViewModel: PlaylistViewModel,
    fileBrowserViewModel: FileBrowserViewModel,
    titleSuffix: String,
    askForCrashReporting: Boolean = true
) {
    val preferencesDataSource: PreferencesDataSource = koinInject()
    val crashReporter: CrashReporter = koinInject()
    var showCrashReportingOptIn by remember {
        mutableStateOf(askForCrashReporting && !preferencesDataSource.isCrashreportingEnabledDialogShown())
    }

    QDeqTheme {
        QDeqNavigation(
            playerViewModel = playerViewModel,
            queueViewModel = queueViewModel,
            playlistViewModel = playlistViewModel,
            fileBrowserViewModel = fileBrowserViewModel,
            titleSuffix = titleSuffix
        )

        if (showCrashReportingOptIn) {
            CrashReportingOptInDialog { enabled ->
                preferencesDataSource.setCrashreportingEnabled(enabled)
                preferencesDataSource.setCrashreportingEnabledDialogShown(true)
                crashReporter.setEnabled(enabled)
                showCrashReportingOptIn = false
            }
        }
    }
}
