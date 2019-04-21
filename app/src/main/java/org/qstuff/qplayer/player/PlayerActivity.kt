package org.qstuff.qplayer.player


import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.WarwickWestonWright.HGDialV2.HGDialInfo
import com.WarwickWestonWright.HGDialV2.HGDialV2
import com.WarwickWestonWright.HGDialV2.HGViewContainer

import android.os.Bundle
import android.text.Html
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_player.*
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.R
import org.qstuff.qplayer.playlists.PlaylistFragment
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.filebrowser.FileBrowserFragment
import org.qstuff.qplayer.queue.QueueFragment
import org.qstuff.qplayer.queue.QueueViewModel
import org.qstuff.qplayer.util.PlayerStatus
import org.qstuff.qplayer.util.TrackRepeatStatus
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 *
 */
class PlayerActivity : AppCompatActivity() {

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
        waveformViewLoadingText.visibility = View.GONE

        setupTitle()
        setupJogWheel()
        setupContentSection()
        setupInteractionListener()
        setupPitchRangeSpinner()

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
    // Interaction Listeners
    //

    private fun setupInteractionListener() {

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
            queueViewModel.toggleRepeat()
        }

        buttonShuffle.setOnClickListener {
            queueViewModel.toggleShuffle()
        }

        buttonMasterTempo.setOnClickListener {
            playerViewModel.toggleMasterTempo()
        }

        buttonCue.setOnClickListener {
            currentTrack?.also {
                cuepointView.cuepointPosition = trackProgressBar.progress
                cuepointView.visibility = View.VISIBLE
                playerViewModel.toggleCue(it, true)
            }
        }

        buttonCue.setOnLongClickListener {
            currentTrack?.also {
                cuepointView.cuepointPosition = trackProgressBar.progress
                cuepointView.visibility = View.GONE
                playerViewModel.toggleCue(it, false)
            }
            true
        }

        buttonPitchReset.setOnClickListener {
            pitchControl.reset()
        }

        buttonPitchControlIncrease.setOnClickListener {
            val current = pitchControl.progress
            val delta = (0.1f * playerViewModel.pitchFactor).toInt()
            pitchControl.setNewProgress(current + delta)
        }

        buttonPitchControlDecrease.setOnClickListener {
            val current = pitchControl.progress
            val delta = (0.1f * playerViewModel.pitchFactor).toInt()
            pitchControl.setNewProgress(current - delta)
        }

        pitchControl.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Timber.d("onProgressChanged(): $progress")
                playerViewModel.onPitchChanged(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    //
    // Viewmodel Observers
    //

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
                        waveformViewLoadingText.visibility = View.VISIBLE
                        trackProgressBar.progress = 0

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
                        playerViewModel.onTrackCompleted(track)
                        queueViewModel.onTrackCompleted(track)
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
                    waveformViewLoadingText.visibility = View.GONE
                } else {
                    Timber.w("onWaveformDataUpdate(): ${trackData.track.name} not ${currentTrack?.name}")
                }
            }
        })

        playerViewModel.masterTempo.observe(this, Observer { masterTempo ->
            masterTempo?.let {
                if (it) {
                    buttonMasterTempo.setTextColor(ContextCompat.getColor(this, R.color.q_orange))
                } else {
                    buttonMasterTempo.setTextColor(ContextCompat.getColor(this, R.color.white))
                }
            }
        })

        playerViewModel.pitchValueText.observe(this, Observer { pitch ->
            pitchControlValueIndicator.text = pitch ?: "0,0%"
        })

        playerViewModel.cueActive.observe(this, Observer { cueActive ->
            cueActive?.let {
                if (it){
                    buttonCue.setTextColor(ContextCompat.getColor(this, R.color.q_orange))
                } else {
                    buttonCue.setTextColor(ContextCompat.getColor(this, R.color.white))
                }
            }
        })

        queueViewModel.repeat.observe(this, Observer { repeatStatus ->
            repeatStatus?.let {
                when (it) {
                    TrackRepeatStatus.NONE -> {
                        buttonRepeat.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.button_loop))
                    }
                    TrackRepeatStatus.ONE -> {
                        buttonRepeat.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.button_loop1_selected))
                    }
                    TrackRepeatStatus.ALL -> {
                        buttonRepeat.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.button_loop_selected))
                    }
                }
            }
        })

        queueViewModel.shuffle.observe(this, Observer { shuffle ->
            shuffle?.let {
                if (it) {
                    buttonShuffle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.button_shuffle_selected))
                } else {
                    buttonShuffle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.button_shuffle))
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

                onJogWheeMoved(0.0f)
            }

            override fun onMove(hgDialInfo: HGDialInfo?) {
                val angle = (hgDialInfo?.textureAngle!! * 100).toFloat()

                onJogWheeMoved(angle)
            }
        })
    }

    private fun onJogWheeMoved(angle: Float) {
        if ((pitchControl.progress + angle) < 0) return

        val current = pitchControl.progress
        playerViewModel.onPitchChanged((current + (angle * 10)).toInt())
    }

    private fun setupContentSection() {

        contentPager.apply {
            adapter = ContentPagerAdapter(context, supportFragmentManager)
            offscreenPageLimit = 2
        }
        contentTabbar.apply {
            setViewPager(contentPager)
        }
    }

    private fun setupPitchRangeSpinner() {

        val spinnerAdapter = ArrayAdapter<String>(
                this, R.layout.spinner_pitch_range, resources.getStringArray(R.array.pitchRangeValues))
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_pitch_range)
        pitchRangeSpinner.adapter = spinnerAdapter
        pitchRangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                playerViewModel.onPitchRangeSelected(position)
                playerViewModel.onPitchChanged(pitchControl.progress)
            }
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

        if (!isBlinkAnimationRunning) {
            Timber.d("startRemainBlinkAnimation():")

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
            Timber.d("stopRemainBlinkAnimation():")

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
    private class ContentPagerAdapter(val context: Context, fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

        override fun getItem(position: Int): Fragment {
            when(position) {
                0 -> return QueueFragment.newInstance()
                1 -> return FileBrowserFragment.newInstance()
                2 -> return PlaylistFragment.newInstance()
            }
            return null!!
        }

        override fun getCount() = 3

        override fun getPageTitle(position: Int): CharSequence? {
            when (position) {
                0 -> return context.getString(R.string.queue_title)
                1 -> return context.getString(R.string.filebrowser_title)
                2 -> return context.getString(R.string.playlists_title)
            }
            return ""
        }
    }

    private inner class TrackProgressChangedListener : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            if (currentTrack == null) return

            val dTotal = currentTrack!!.duration.toDouble() / 1000
            val dProgress = seekBar.progress.toDouble() / 1000
            playerViewModel.seekTo((dProgress * dTotal * 1000), false)
        }
    }
}
