package org.qstuff.qplayer.ui.player

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.ui.QDeqApp
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.lockOrientationForDevice
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueViewModel
import co.touchlab.kermit.Logger

private val log = Logger.withTag("PlayerActivity")

/**
 * The app's only activity: hosts the shared Compose UI (QDeqApp), the cross-ViewModel wiring and
 * the media service lifecycle.
 */
class PlayerActivity : AppCompatActivity() {

    // Activity-scoped, created by Koin (see di/ViewModelModule); PlaybackCoordinator wires them together.
    private val playerViewModel: PlayerViewModel by viewModel()
    private val queueViewModel: QueueViewModel by viewModel()
    private val playlistViewModel: PlaylistViewModel by viewModel()
    private val fileBrowserViewModel: FileBrowserViewModel by viewModel()

    private val preferencesDataSource: PreferencesDataSource by inject()

    private val playbackCoordinator by lazy {
        PlaybackCoordinator(
            playerViewModel, queueViewModel, playlistViewModel, fileBrowserViewModel, preferencesDataSource
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.d { "onCreate()" }
        // Phones: portrait. Tablets: landscape two-pane layout (see PlayerScreen). The manifest's
        // configChanges keep the activity alive when this rotates it — onDestroy stops playback.
        lockOrientationForDevice(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        playerViewModel.connectPlayer()

        // Wiring between player and queue, settings, restore — see PlaybackCoordinator. Started in
        // lifecycleScope: it runs in the background too and ends when the activity is destroyed.
        playbackCoordinator.start(lifecycleScope)

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) { BuildConfig.VERSION_NAME }

        val titleSuffix = if (BuildConfig.DEBUG) {
            "-dev $versionName (${getVersionCode()}) | API-${Build.VERSION.SDK_INT} | ${(application as QDeqApplication).getDPI()}"
        } else {
            " v$versionName"
        }

        setContent {
            QDeqApp(
                playerViewModel = playerViewModel,
                queueViewModel = queueViewModel,
                playlistViewModel = playlistViewModel,
                fileBrowserViewModel = fileBrowserViewModel,
                titleSuffix = titleSuffix
            )
        }
    }

    override fun onStop() {
        super.onStop()
        playbackCoordinator.saveState()
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackCoordinator.saveState()
        playerViewModel.disconnectPlayer()
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        } else {
            packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
        }
}
