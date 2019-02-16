package org.qstuff.qplayer.player


import android.content.pm.PackageManager
import android.os.Build
import com.WarwickWestonWright.HGDialV2.HGDialInfo
import com.WarwickWestonWright.HGDialV2.HGDialV2
import com.WarwickWestonWright.HGDialV2.HGViewContainer

import android.os.Bundle
import android.text.Html
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_player.*
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.R
import org.qstuff.qplayer.contentbrowser.ContentListFragment
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.filebrowser.FileBrowserFragment
import org.qstuff.qplayer.queue.QueueFragment
import timber.log.Timber

/**
 *
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var jogWheelContainer: HGViewContainer
    private lateinit var jogWheelDial: HGDialV2
    private lateinit var jogWheelInterface: HGDialV2.IHGDial

    private lateinit var playerViewModel: PlayerViewModel

    private lateinit var currentTrack: Track


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerViewModel = ViewModelProviders.of(this).get(PlayerViewModel::class.java)
        playerViewModel.startMediaService()

        waveformView.updateWaveform(null)

        setupTitle()
        setupJogWheel()
        setupContentSection()
        setupClickListener()

        playerViewModel.onMediaServiceConnected.observe(this, Observer { connected ->
            Timber.d("onMediaServiceConnected(): $connected")
            if(connected) {
                setupObservers()
            } else {
                // TODO: remove observers ?
            }
        })
    }

    override fun onStart() {
        super.onStart()

        jogWheelDial.registerCallback(jogWheelInterface)
    }

    override fun onStop() {
        super.onStop()

        jogWheelDial.unRegisterCallback()
    }

    override fun onDestroy() {
        super.onDestroy()

        playerViewModel.stopMediaService()
    }

    //
    // private
    //

    private fun setupClickListener() {

        buttonPlayPause.setOnClickListener {
            playerViewModel.playPause()
            updatePlayButtonUI()
        }

        buttonPrevious.setOnClickListener {

        }

        buttonNext.setOnClickListener {

        }

        buttonRepeat.setOnClickListener {

        }

        buttonShuffle.setOnClickListener {

        }

        buttonMastertempo.setOnClickListener {

        }

        buttonCue.setOnClickListener {

        }

        buttonCue.setOnLongClickListener {

            true
        }

        buttonPitchReset.setOnClickListener {

        }

        buttonPitchControlIncrease.setOnClickListener {

        }

        buttonPitchControlDecrease.setOnClickListener {

        }
    }

    private fun setupObservers() {

        playerViewModel.onPlayerStatusMediator.observe(this, Observer { track ->

            Timber.d("onPlayerStatusUpdate(): $track")

            track?.let {
                when (track.trackStatus) {
                    Track.TrackStatus.LOADING -> {
                        currentTrack = track
                        trackTitle.text = track.name
                    }
                    Track.TrackStatus.PREPARED -> {
                        // TODO: Autoplay?
                    }
                    Track.TrackStatus.COMPLETED -> {
                        // TODO: Continous Play?
                    }
                    Track.TrackStatus.ERROR -> {
                        // TODO: Error message?
                    }
                    else -> {}
                }
            }
        })

        playerViewModel.onWaveformDataUpdate.observe(this, Observer { trackData ->
            Timber.d("onWaveformDataUpdate():")

            trackData?.let {
                if (trackData.track == currentTrack) {
                    waveformView.updateWaveform(null)
                    waveformView.updateWaveform(trackData)
                } else {
                    Timber.w("onWaveformDataUpdate(): ${trackData.track.name} not ${currentTrack.name}")
                }
            }
        })
    }

    private fun setupTitle() {

        var debugTitleSuffix =""
        if (BuildConfig.DEBUG) {
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                debugTitleSuffix = ("-α build: ${packageInfo.versionCode} API-${Build.VERSION.SDK_INT} ${(application as QDeqApplication).getDPI()})")
            } catch(e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
        playerTitle.text = Html.fromHtml("<font color=#FC7614>q</font><font color=#ffffff>deq</font>" + debugTitleSuffix)
    }

    private fun setupJogWheel() {
        jogWheelContainer = com.WarwickWestonWright.HGDialV2.HGViewContainer(R.drawable.qpl_btn_wheel_ohne_rand01, jogWheel)
        jogWheelDial = jogWheelContainer.hgDialV2Active
        jogWheelInterface = (object: HGDialV2.IHGDial {

            override fun onDown(p0: HGDialInfo?) {}
            override fun onPointerDown(p0: HGDialInfo?) {}
            override fun onPointerUp(p0: HGDialInfo?) {}

            override fun onUp(p0: HGDialInfo?) {
                jogWheelDial.doManualTextureDial(0.0)

                // FIXME: onJogWheelMoved.onNext(0f)
            }

            override fun onMove(hgDialInfo: HGDialInfo?) {
                val angle = (hgDialInfo?.textureAngle!! * 100).toFloat()

                // FIXME: onJogWheelMoved.onNext(angle)
            }
        })
    }

    private fun setupContentSection() {

        contentPager.apply {
            adapter = ContentPagerAdapter(supportFragmentManager)
            offscreenPageLimit = 2
        }
        contentTabbar.apply {
            setViewPager(contentPager)
        }
    }

    //
    // UI Control
    //

    private fun updatePlayButtonUI() {
        if (playerViewModel.isTrackPlaying) {
            buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.button_pause_selected))
            // TODO: VM -> startUpdateTimer()
        } else {
            buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.button_play_selected))
            // TODO: VM -> resetUpdateTimer()
        }
    }

    /**
     *
     */
    private class ContentPagerAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {
        override fun getItem(position: Int): Fragment {
            when(position) {
                0 -> return QueueFragment.newInstance()
                1 -> return FileBrowserFragment.newInstance()
                2 -> return ContentListFragment.newInstance()
            }
            return null!!
        }

        override fun getCount() = 3

        override fun getPageTitle(position: Int): CharSequence? {
            when (position) {
                0 -> return "queue"
                1 -> return "filebrowser"
                2 -> return "playlists"
            }
            return ""
        }
    }
}
