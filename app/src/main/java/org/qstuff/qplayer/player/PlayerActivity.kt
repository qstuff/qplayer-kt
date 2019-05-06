package org.qstuff.qplayer.player


import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.activity_player.*
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.R
import org.qstuff.qplayer.playlists.PlaylistFragment
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.filebrowser.FileBrowserFragment
import org.qstuff.qplayer.queue.QueueFragment
import org.qstuff.qplayer.queue.QueueViewModel
import org.qstuff.qplayer.settings.SettingsActivity
import org.qstuff.qplayer.settings.WebViewActivity
import org.qstuff.qplayer.util.PlayerStatus
import org.qstuff.qplayer.util.TrackRepeatStatus
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 *
 */
class PlayerActivity : AppCompatActivity(), KoinComponent {

    companion object {
        const val EXTRA_URL = "EXTRA_URL"
        const val HTMLPAGE_PRIVACY = "privacy.html"
        const val HTMLPAGE_IMPRINT = "imprint.html"
        const val HTMLPAGE_CONTACT = "contact.html"
        const val HTMLPAGE_LICENSES = "licenses.html"
    }

    private lateinit var jogWheelContainer: HGViewContainer
    private lateinit var jogWheelDial: HGDialV2
    private lateinit var jogWheelInterface: HGDialV2.IHGDial

    private val remainBlinkAnimation = AlphaAnimation(0.0f, 1.0f)

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var queueViewModel: QueueViewModel

    private val preferencesDataSource by inject<PreferencesDataSource>()

    private var currentTrack: Track? = null

    private var isTrackPrepared = false
    private var isBlinkAnimationRunning = false
    private var isCueActive = false
    private var showRemainingTime = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate()")

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
        setupInteractionListeners()
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

    override fun onResume() {
        super.onResume()

        setupCrashlytics()
        playerViewModel.loadSettings()
    }

    override fun onStart() {
        super.onStart()

        jogWheelDial.registerCallback(jogWheelInterface)
    }

    override fun onPause() {
        super.onPause()

        playerViewModel.saveState()
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
    // ViewModel Observers
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
                currentTrack = track

                when (track.trackStatus) {
                    Track.TrackStatus.LOADING -> {
                        trackTitle.text = "loading..."
                        stopRemainBlinkAnimation()
                        waveformView.updateWaveform(null)
                        waveformViewLoadingText.visibility = View.VISIBLE
                        trackProgressBar.progress = 0
                    }
                    Track.TrackStatus.PREPARED -> {
                        isTrackPrepared = true
                        stopRemainBlinkAnimation()
                        trackProgressBar.progress = 0
                        trackTitle.text = track.name
                        totalTrackLength.text = "total: ${getDurationHumanReadable(track.duration)}"
                        dynamicTrackLength.text = "remain: ${getDurationHumanReadable(track.duration)}"

                        playerViewModel.seekTo(track.playPosition.toDouble(), track.isAutoplay)

                        if (track.isAutoplay) {
                            playerViewModel.playPause()
                        }
                        if (track.cuePosition > 0L) {
                            // TODO restore cue
                        }

                    }
                    Track.TrackStatus.COMPLETED -> {
                        stopRemainBlinkAnimation()
                        playerViewModel.onTrackCompleted(track)
                        queueViewModel.onTrackCompleted(track)
                        track.playPosition = 0
                    }
                    Track.TrackStatus.ERROR -> {
                        // TODO: Error message?
                    }
                    else -> {}
                }
            }
        })

        playerViewModel.showRemainingTime.observe(this, Observer {  showRemain ->
            showRemainingTime = showRemain
        })

        playerViewModel.onTrackPositionUpdate.observe(this, Observer { position ->
            Timber.v("onTrackPositionUpdate(): $position")

            var pos = position ?: 0
            currentTrack?.let {
                if (showRemainingTime) {
                    dynamicTrackLength.text = "remain: ${getDurationHumanReadable(it.duration - pos)}"
                } else {
                    dynamicTrackLength.text = "current: ${getDurationHumanReadable(pos)}"
                }

                if ((it.duration - pos) < 30000) {
                    startRemainBlinkAnimation()
                } else {
                    stopRemainBlinkAnimation()
                }

                updateTrackProgressIndicator(pos)
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

        playerViewModel.pitchValueText.observe(this, Observer { pitchValueText ->
            pitchControlValueIndicator.text = pitchValueText ?: "0,0%"
        })

        playerViewModel.pitchValue.observe(this, Observer { pitchValue ->
            pitchControl.setNewProgress(pitchValue)
        })

        playerViewModel.pitchFactorIndex.observe(this, Observer { pitchFactorIndex ->
            pitchRangeSpinner.setSelection(pitchFactorIndex)
        })

        playerViewModel.cueActive.observe(this, Observer { cueActive ->
            cueActive?.let {
                isCueActive = cueActive

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

    //
    // Interaction Listeners
    //

    private fun setupInteractionListeners() {

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
            playerViewModel.onPitchChanged(pitchControl.progress)
        }

        buttonCue.setOnClickListener {
            if (isCueActive) {
                playerViewModel.playFromCue(currentTrack)
            } else {
                currentTrack?.also {
                    cuepointView.cuepointPosition = trackProgressBar.progress
                    cuepointView.visibility = View.VISIBLE
                    playerViewModel.toggleCue(it, true)
                }
            }
        }

        buttonCue.setOnLongClickListener {
            if (isCueActive) {
                currentTrack?.also {
                    cuepointView.cuepointPosition = trackProgressBar.progress
                    cuepointView.visibility = View.GONE
                    playerViewModel.toggleCue(it, false)
                }
            }
            true
        }

        dynamicTrackLength.setOnClickListener {
            playerViewModel.toggleDynamicTrackLengthDisplay()
        }

        buttonPitchReset.setOnClickListener {
            pitchControl.reset()
            playerViewModel.onPitchChanged(pitchControl.progress)
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

        pitchControl.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Timber.d("onProgressChanged(): $progress")
                playerViewModel.onPitchChanged(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        moreMenu.setOnClickListener {
            val popup = PopupMenu(this, moreMenu)
            val inflater = popup.menuInflater
            inflater.inflate(R.menu.more_menu, popup.menu)
            popup.setOnMenuItemClickListener {item ->

                when (item.itemId) {
                    R.id.more_menu_settings -> {
                        startSettingsActivity()
                        true
                    }
                    R.id.more_menu_privacy -> {
                        startWebViewActivity(HTMLPAGE_PRIVACY)
                        true
                    }
                    R.id.more_menu_imprint -> {
                        startWebViewActivity(HTMLPAGE_IMPRINT)
                        true
                    }
                    R.id.more_menu_licenses -> {
                        startWebViewActivity(HTMLPAGE_LICENSES)
                        true
                    }
                    else -> false
                }

                false
            }
            popup.show()
        }
    }

    //
    // Private
    //

    private fun setupTitle() {

        var debugTitleSuffix =""
        if (BuildConfig.DEBUG) {
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                debugTitleSuffix = ("-α ${packageInfo.versionName} (${packageInfo.versionCode}) | API-${Build.VERSION.SDK_INT} | ${(application as QDeqApplication).getDPI()}")
            } catch(e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
        playerTitle.text = Html.fromHtml("<font color=#FC7614>q</font><font color=#ffffff>deq</font>" + debugTitleSuffix)
    }

    private fun setupJogWheel() {
        jogWheelContainer = HGViewContainer(R.drawable.qpl_btn_wheel_ohne_rand01, jogWheel)
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

    private fun startWebViewActivity(url: String) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra(EXTRA_URL, url)
        startActivity(intent)
    }

    private fun startSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun setupCrashlytics() {

        if (!preferencesDataSource.isCrashreportingEnabledDialogShown()) {
            preferencesDataSource.setCrashreportingEnabledDialogShown(true)
            showEnableCrashReportDialog()
            return
        }

        val isCrashreportingEnabled = preferencesDataSource.isCrashreportingEnabled()
        val core = CrashlyticsCore.Builder().disabled(!isCrashreportingEnabled).build()
        Fabric.with(this, Crashlytics.Builder().core(core).build())
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
    // Dialogs
    //

    private fun showEnableCrashReportDialog() {

        AlertDialog.Builder(this)
                .apply {
                    setCancelable(false)
                    setTitle(getString(R.string.dialog_crashlytics_opt_in_title))
                    setMessage(getString(R.string.dialog_crashlytics_opt_in_message))
                    setPositiveButton(getString(R.string.dialog_crashlytics_opt_in_go_to_settings)) { dialog, which ->
                        startSettingsActivity()
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.dialog_crashlytics_opt_in_no_thanks)) { dialog, which ->
                        dialog.dismiss()
                    }
                }.show()
    }

    //
    // Inner classes
    //

    private class ContentPagerAdapter(val context: Context, fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

        override fun getItem(position: Int) =
                when (position) {
                    0 -> QueueFragment.newInstance()
                    1 -> FileBrowserFragment.newInstance()
                    2 -> PlaylistFragment.newInstance()
                    else -> null!!
                }

        override fun getCount() = 3

        override fun getPageTitle(position: Int) =
                when (position) {
                    0 -> context.getString(R.string.queue_title)
                    1 -> context.getString(R.string.filebrowser_title)
                    2 -> context.getString(R.string.playlists_title)
                    else -> ""
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
