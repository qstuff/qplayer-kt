package org.qstuff.qplayer.ui.player

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.ui.QDeqNavigation
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.lockOrientationForDevice
import org.qstuff.qplayer.ui.player.mediaservice.QMediaPlayerService
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueViewModel
import org.qstuff.qplayer.ui.settings.CrashReportingOptInDialog
import org.qstuff.qplayer.ui.theme.QDeqTheme
import org.qstuff.qplayer.util.CrashReporting
import co.touchlab.kermit.Logger

private val log = Logger.withTag("PlayerActivity")

/**
 * The app's only activity: hosts the Compose UI (player, Settings, Licenses, info pages — see
 * QDeqNavigation), the cross-ViewModel wiring and the media service lifecycle.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var queueViewModel: QueueViewModel
    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var fileBrowserViewModel: FileBrowserViewModel

    private val preferencesDataSource: PreferencesDataSource by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.d { "onCreate()" }
        // Phones: portrait. Tablets: landscape two-pane layout (see PlayerScreen). The manifest's
        // configChanges keep the activity alive when this rotates it — onDestroy stops playback.
        lockOrientationForDevice(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        playerViewModel = ViewModelProvider(this).get(PlayerViewModel::class.java)
        playerViewModel.startMediaService()
        queueViewModel = ViewModelProvider(this).get(QueueViewModel::class.java)
        playlistViewModel = ViewModelProvider(this).get(PlaylistViewModel::class.java)
        fileBrowserViewModel = ViewModelProvider(this).get(FileBrowserViewModel::class.java)

        // Cross-ViewModel wiring. Deliberately NOT gated on the STARTED state (plain lifecycleScope
        // instead of repeatOnLifecycle): auto-advance must load and start the next track while the
        // app is in the background, too. The collectors stop when the activity is destroyed.
        //
        // queue track selection → player load. Collecting starts right here (lifecycleScope runs on
        // Main.immediate), before readSelectedTrack() below emits the track restored on start.
        lifecycleScope.launch {
            queueViewModel.trackSelected.collect { track -> playerViewModel.loadTrack(track) }
        }
        // track finished → the queue decides what follows (repeat/shuffle); if nothing does,
        // rewind so Play starts the finished track again
        lifecycleScope.launch {
            playerViewModel.trackCompleted.collect { track ->
                if (!queueViewModel.onTrackCompleted(track)) {
                    playerViewModel.stopAfterCompletion()
                }
            }
        }
        // Next/Prev from system media controls (notification, lock screen, headset, Bluetooth)
        // → same behavior as the on-screen buttons (Prev is position-aware)
        lifecycleScope.launch {
            playerViewModel.transportCommands.collect { command ->
                val current = playerViewModel.playerTrackState.value?.track
                when (command) {
                    QMediaPlayerService.TransportCommand.NEXT ->
                        queueViewModel.nextTrack(current)
                    QMediaPlayerService.TransportCommand.PREVIOUS ->
                        if (!playerViewModel.restartCurrentTrackIfPlayed()) {
                            queueViewModel.previousTrack(current)
                        }
                }
            }
        }
        queueViewModel.readTrackList()
        queueViewModel.readSelectedTrack()
        queueViewModel.loadStates()
        playlistViewModel.loadPlaylists()

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) { BuildConfig.VERSION_NAME }

        val titleSuffix = if (BuildConfig.DEBUG) {
            "-dev $versionName (${getVersionCode()}) | API-${Build.VERSION.SDK_INT} | ${(application as QDeqApplication).getDPI()}"
        } else {
            " v$versionName"
        }

        setContent {
            // Crash reporting opt-in: asked once, then changeable in Settings.
            var showCrashReportingOptIn by remember {
                mutableStateOf(!preferencesDataSource.isCrashreportingEnabledDialogShown())
            }

            QDeqTheme {
                QDeqNavigation(
                    playerViewModel = playerViewModel,
                    queueViewModel = queueViewModel,
                    playlistViewModel = playlistViewModel,
                    fileBrowserViewModel = fileBrowserViewModel,
                    titleSuffix = titleSuffix,
                    onSettingsClosed = ::loadSettings
                )

                if (showCrashReportingOptIn) {
                    CrashReportingOptInDialog { enabled ->
                        preferencesDataSource.setCrashreportingEnabled(enabled)
                        preferencesDataSource.setCrashreportingEnabledDialogShown(true)
                        CrashReporting.apply(enabled)
                        showCrashReportingOptIn = false
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
    }

    override fun onPause() {
        super.onPause()
        playerViewModel.saveState()
    }

    override fun onStop() {
        super.onStop()
        // formerly in FileBrowserFragment.onStop
        fileBrowserViewModel.saveLastBrowsedDir()
    }

    override fun onDestroy() {
        super.onDestroy()
        // formerly in QueueFragment.onDestroyView
        queueViewModel.saveStates()
        playerViewModel.stopMediaService()
    }

    /** Apply the (possibly changed) settings to the player and the queue. */
    private fun loadSettings() {
        playerViewModel.loadSettings()
        queueViewModel.loadSettings()
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        } else {
            packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
        }
}
