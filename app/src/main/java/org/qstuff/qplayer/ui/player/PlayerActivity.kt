package org.qstuff.qplayer.ui.player

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueViewModel
import org.qstuff.qplayer.ui.settings.SettingsActivity
import org.qstuff.qplayer.ui.settings.WebViewActivity
import org.qstuff.qplayer.ui.theme.QDeqTheme
import timber.log.Timber

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "EXTRA_URL"
        const val HTMLPAGE_PRIVACY = "privacy.html"
        const val HTMLPAGE_IMPRINT = "imprint.html"
        const val HTMLPAGE_LICENSES = "licenses.html"
    }

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var queueViewModel: QueueViewModel
    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var fileBrowserViewModel: FileBrowserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate()")

        playerViewModel = ViewModelProvider(this).get(PlayerViewModel::class.java)
        playerViewModel.startMediaService()
        queueViewModel = ViewModelProvider(this).get(QueueViewModel::class.java)
        playlistViewModel = ViewModelProvider(this).get(PlaylistViewModel::class.java)
        fileBrowserViewModel = ViewModelProvider(this).get(FileBrowserViewModel::class.java)

        // Cross-ViewModel wiring: queue track selection → player load
        // (formerly in QueueFragment.onViewCreated)
        queueViewModel.onTrackSelected.observe(this) { track ->
            track?.let { playerViewModel.loadTrack(it) }
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
            QDeqTheme {
                PlayerScreen(
                    playerViewModel = playerViewModel,
                    queueViewModel = queueViewModel,
                    playlistViewModel = playlistViewModel,
                    fileBrowserViewModel = fileBrowserViewModel,
                    titleSuffix = titleSuffix,
                    onOpenSettings = ::startSettingsActivity,
                    onOpenWebView = ::startWebViewActivity
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playerViewModel.loadSettings()
        queueViewModel.loadSettings()
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

    private fun startWebViewActivity(url: String) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra(EXTRA_URL, url)
        startActivity(intent)
    }

    private fun startSettingsActivity() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        } else {
            packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
        }
}
