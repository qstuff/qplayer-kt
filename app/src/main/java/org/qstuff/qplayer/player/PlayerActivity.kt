package org.qstuff.qplayer.player


import android.content.pm.PackageManager
import android.os.Build
import com.WarwickWestonWright.HGDialV2.HGDialInfo
import com.WarwickWestonWright.HGDialV2.HGDialV2
import com.WarwickWestonWright.HGDialV2.HGViewContainer

import android.os.Bundle
import android.text.Html
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.SeekBar
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
import org.qstuff.qplayer.queue.QueueViewModel
import org.qstuff.qplayer.util.PlayerStatus
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 *
 */
class PlayerActivity : AppCompatActivity() {

    companion object {

    }

    private lateinit var jogWheelContainer: HGViewContainer
    private lateinit var jogWheelDial: HGDialV2
    private lateinit var jogWheelInterface: HGDialV2.IHGDial

    private val remainBlinkAnimation = AlphaAnimation(0.0f, 1.0f)

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var queueViewModel: QueueViewModel

    private var currentTrack: Track? = null

    private var isTrackPrepared = false
    private var isBlinkAnimationRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerViewModel = ViewModelProviders.of(this).get(PlayerViewModel::class.java)
        playerViewModel.startMediaService()

        queueViewModel = ViewModelProviders.of(this).get(QueueViewModel::class.java)


        trackProgressBar.setOnSeekBarChangeListener(TrackProgressChangedListener())
        trackProgressBar.progress = 0

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
            if (isTrackPrepared) {
                playerViewModel.playPause()
            } else {
                Timber.w("buttonPlayPause(): track not prepared")
            }
        }

        buttonPrevious.setOnClickListener {
            queueViewModel.previousTrack(currentTrack)
        }

        buttonNext.setOnClickListener {
            queueViewModel.nextTrack(currentTrack)
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

        playerViewModel.playerStatus.observe(this, Observer { status ->
            Timber.d("playerStatus(): $status")

            when(status) {
                PlayerStatus.PLAYING -> {
                    updatePlayButtonUI(true)
                }
                PlayerStatus.PAUSED -> {
                    updatePlayButtonUI(false)

                }
                else -> {}
            }

        })

        playerViewModel.trackStatusMediator.observe(this, Observer { track ->
            Timber.d("trackStatus(): $track")

            track?.let {
                isTrackPrepared = false

                when (track.trackStatus) {
                    Track.TrackStatus.LOADING -> {
                        currentTrack = track
                        trackTitle.text = track.name
                        stopRemainBlinkAnimation()
                        waveformView.updateWaveform(null)

                    }
                    Track.TrackStatus.PREPARED -> {

                        isTrackPrepared = true
                        totalTrackLength.text = "total: ${getDurationHumanReadable(track.duration)}"
                        dynamicTrackLength.text = "remain: ${getDurationHumanReadable(track.duration)}"

                        if (track.isAutoplay) {
                            playerViewModel.playPause()
                        }

                    }
                    Track.TrackStatus.COMPLETED -> {

                        stopRemainBlinkAnimation()
                        playerViewModel.trackCompleted()
                    }
                    Track.TrackStatus.ERROR -> {
                        // TODO: Error message?
                    }
                    else -> {}
                }
            }
        })

        playerViewModel.onTrackPositionUpdate.observe(this, Observer { position ->
            Timber.v("onTrackPositionUpdate(): $position")

            currentTrack?.let {
                dynamicTrackLength.text = "remain: ${getDurationHumanReadable(it.duration - position)}"
                if ((it.duration - position) < 30000) {
                    startRemainBlinkAnimation()
                } else {
                    stopRemainBlinkAnimation()
                }

                updateTrackProgressIndicator(position)
            }
        })

        playerViewModel.onWaveformDataUpdate.observe(this, Observer { trackData ->
            Timber.d("onWaveformDataUpdate():")

            trackData?.let {
                if (trackData.track == currentTrack) {
                    waveformView.updateWaveform(trackData)
                } else {
                    Timber.w("onWaveformDataUpdate(): ${trackData.track.name} not ${currentTrack?.name}")
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

    private fun updateTrackProgressIndicator(position: Long) {
        val dTotal = currentTrack!!.duration.toDouble()
        val dPosition = position.toDouble()
        trackProgressBar.progress = ((dPosition / dTotal) * 1000).toInt()
    }

    private fun updatePlayButtonUI(playing: Boolean) {
        if (playing) {
            buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.button_pause_selected))

        } else {
            buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.button_play_selected))
            // TODO: VM -> resetUpdateTimer()
        }
    }

    private fun startRemainBlinkAnimation() {
        Timber.d("startRemainBlinkAnimation():")

        if (!isBlinkAnimationRunning) {
            remainBlinkAnimation.apply {
                duration = 700
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
                start()
            }
            dynamicTrackLength.animation = remainBlinkAnimation
            isBlinkAnimationRunning = true
        }
    }

    private fun stopRemainBlinkAnimation() {
        if (isBlinkAnimationRunning) {
            dynamicTrackLength.clearAnimation()
            remainBlinkAnimation.reset()
            isBlinkAnimationRunning = false
        }
    }

    private fun getDurationHumanReadable(time: Long) =
            String.format("%02d:%02d:%02d",
                    TimeUnit.MILLISECONDS.toHours(time),
                    TimeUnit.MILLISECONDS.toMinutes(time) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)), // The change is in this line
                    TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time)))

    //
    // Inner classes
    //
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

    private inner class TrackProgressChangedListener : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            val dTotal = currentTrack!!.duration.toDouble() / 1000
            val dProgress = seekBar.progress.toDouble() / 1000
            playerViewModel.seekTo((dProgress * dTotal * 1000), false)
        }
    }
}
