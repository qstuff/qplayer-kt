package org.qstuff.qplayer.ui.player

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.datasource.model.Track
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

    // Nullable: clearTrackList() posts null.
    private val trackSelectedObserver = Observer<Track?> { track ->
        track?.let { playerViewModel.loadTrack(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate()")

        playerViewModel = ViewModelProvider(this).get(PlayerViewModel::class.java)
        playerViewModel.startMediaService()
        queueViewModel = ViewModelProvider(this).get(QueueViewModel::class.java)
        playlistViewModel = ViewModelProvider(this).get(PlaylistViewModel::class.java)
        fileBrowserViewModel = ViewModelProvider(this).get(FileBrowserViewModel::class.java)

        // Cross-ViewModel wiring. Deliberately NOT gated on the STARTED state (observeForever /
        // plain lifecycleScope instead of observe(this) / repeatOnLifecycle): auto-advance must
        // load and start the next track while the app is in the background, too. Both stop when
        // the activity is destroyed.
        //
        // queue track selection → player load
        queueViewModel.onTrackSelected.observeForever(trackSelectedObserver)
        // track finished → the queue decides what follows (repeat/shuffle); if nothing does,
        // rewind so Play starts the finished track again
        lifecycleScope.launch {
            playerViewModel.trackCompleted.collect { track ->
                if (!queueViewModel.onTrackCompleted(track)) {
                    playerViewModel.seekTo(0.0, true)
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
        queueViewModel.onTrackSelected.removeObserver(trackSelectedObserver)
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
