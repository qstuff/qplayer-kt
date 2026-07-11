package org.qstuff.qplayer.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.ViewModelProvider
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.R
import org.qstuff.qplayer.filebrowser.FileBrowserFragment
import org.qstuff.qplayer.playlists.PlaylistFragment
import org.qstuff.qplayer.queue.QueueFragment
import org.qstuff.qplayer.queue.QueueViewModel
import org.qstuff.qplayer.settings.SettingsActivity
import org.qstuff.qplayer.settings.WebViewActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate()")

        playerViewModel = ViewModelProvider(this).get(PlayerViewModel::class.java)
        playerViewModel.startMediaService()
        queueViewModel = ViewModelProvider(this).get(QueueViewModel::class.java)

        val titleSuffix = if (BuildConfig.DEBUG) {
            try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                "-α ${pi.versionName} (${getVersionCode()}) | API-${Build.VERSION.SDK_INT} | ${(application as QDeqApplication).getDPI()}"
            } catch (e: PackageManager.NameNotFoundException) { "" }
        } else ""

        setContent {
            QDeqTheme {
                PlayerScreen(
                    playerViewModel = playerViewModel,
                    queueViewModel = queueViewModel,
                    titleSuffix = titleSuffix,
                    onOpenSettings = ::startSettingsActivity,
                    onOpenWebView = ::startWebViewActivity,
                    onSetupTabContent = { tabbar, pager ->
                        pager.adapter = ContentPagerAdapter(applicationContext, supportFragmentManager)
                        pager.offscreenPageLimit = 2
                        tabbar.setViewPager(pager)
                    }
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

    override fun onDestroy() {
        super.onDestroy()
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

    private class ContentPagerAdapter(
        val context: Context,
        fragmentManager: FragmentManager
    ) : FragmentPagerAdapter(fragmentManager) {

        override fun getItem(position: Int) =
            when (position) {
                0 -> QueueFragment.newInstance()
                1 -> FileBrowserFragment.newInstance()
                2 -> PlaylistFragment.newInstance()
                else -> null!!
            }

        override fun getCount() = 3

        override fun getPageTitle(position: Int): String =
            when (position) {
                0 -> context.getString(R.string.queue_title)
                1 -> context.getString(R.string.filebrowser_title)
                2 -> context.getString(R.string.playlists_title)
                else -> ""
            }
    }
}
