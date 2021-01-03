package org.qstuff.qplayer.player


import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.lifecycle.ViewModelProvider
import com.WarwickWestonWright.HGDialV2.HGDialInfo
import com.WarwickWestonWright.HGDialV2.HGDialV2
import com.WarwickWestonWright.HGDialV2.HGViewContainer
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.android.synthetic.main.activity_player.*
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.filebrowser.FileBrowserFragment
import org.qstuff.qplayer.playlists.PlaylistFragment
import org.qstuff.qplayer.queue.QueueFragment
import org.qstuff.qplayer.queue.QueueViewModel
import org.qstuff.qplayer.settings.SettingsActivity
import org.qstuff.qplayer.settings.WebViewActivity
import org.qstuff.qplayer.util.*
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
        const val HTMLPAGE_LICENSES = "licenses.html"
    }

    private lateinit var jogWheelContainer: HGViewContainer
    private lateinit var jogWheelDial: HGDialV2
    private lateinit var jogWheelInterface: HGDialV2.IHGDial
    private var currentPitchProgress = 0
    private var jogwheelSensitivity = 10

    private val remainBlinkAnimation = AlphaAnimation(0.4f, 1.0f)

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var queueViewModel: QueueViewModel

    private val preferencesDataSource by inject<PreferencesDataSource>()

    private var currentTrack: Track? = null

    private var isTrackPrepared = false
    private var isBlinkAnimationRunning = false
    private var isCueActive = false
    private var showRemainingTime = true

    private var onMoveTime = 0L
    private var lastOnMoveTime = 0L
    private var lastAngle = 0.0
    private val deltaList = arrayListOf<Double>()

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate()")

        setContentView(R.layout.activity_player)

        playerViewModel = ViewModelProvider(this).get(PlayerViewModel::class.java)
        playerViewModel.startMediaService()

        queueViewModel = ViewModelProvider(this).get(QueueViewModel::class.java)

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
        queueViewModel.loadSettings()
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
                        Track.TrackStatus.UNDEFINED,
                        Track.TrackStatus.LOADING -> {
                        trackTitle.text = getString(R.string.player_loading)
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
                }
            }
        })

        playerViewModel.showRemainingTime.observe(this, Observer {  showRemain ->
            showRemainingTime = showRemain
        })

        playerViewModel.onTrackPositionUpdate.observe(this, Observer { position ->
            Timber.v("onTrackPositionUpdate(): $position")

            val pos = position ?: 0

            currentTrack?.let {
                if (showRemainingTime) {
                    dynamicTrackLength.text = "remain: ${getDurationHumanReadable(it.duration - pos)}"
                } else {
                    dynamicTrackLength.text = "current: ${getDurationHumanReadable(pos)}"
                }

                if ((it.duration - pos) in 0..30000) {
                    startRemainBlinkAnimation(1000)
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
            pitchControl.setNewProgress(pitchValue, false)
        })

        playerViewModel.jogwheelSensitivity.observe(this, Observer { jogwheelSensitivity ->
            this.jogwheelSensitivity = jogwheelSensitivity
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
            when {
                isTrackPrepared -> playerViewModel.playPause()
                currentTrack?.trackStatus == Track.TrackStatus.COMPLETED -> playerViewModel.playPause()
                else -> Timber.w("buttonPlayPause(): track neither prepared or completed")
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
            pitchControl.setNewProgress(current + delta, true)
        }

        buttonPitchControlDecrease.setOnClickListener {
            val current = pitchControl.progress
            val delta = (0.1f * playerViewModel.pitchFactor).toInt()
            pitchControl.setNewProgress(current - delta, true)
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
                    }
                    R.id.more_menu_privacy -> {
                        startWebViewActivity(HTMLPAGE_PRIVACY)
                    }
                    R.id.more_menu_imprint -> {
                        startWebViewActivity(HTMLPAGE_IMPRINT)
                    }
                    R.id.more_menu_licenses -> {
                        startWebViewActivity(HTMLPAGE_LICENSES)
                    }
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
                debugTitleSuffix = ("-α ${packageInfo.versionName} (${getVersionCode()}) | API-${Build.VERSION.SDK_INT} | ${(application as QDeqApplication).getDPI()}")
            } catch(e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
        playerTitle.text = Html.fromHtml("<font color=#FC7614>q</font><font color=#ffffff>deq</font>$debugTitleSuffix")
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode
            } else {
                packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
            }

    private fun setupJogWheel() {
        jogWheelContainer = HGViewContainer(R.drawable.qpl_btn_wheel_ohne_rand01, jogWheel)
        jogWheelDial = jogWheelContainer.hgDialV2Active
        jogWheelInterface = (object: HGDialV2.IHGDial {

            override fun onPointerDown(p0: HGDialInfo?) {}
            override fun onPointerUp(p0: HGDialInfo?) {}

            override fun onDown(hgDialInfo: HGDialInfo?) {
                currentPitchProgress = pitchControl.progress
                onMoveTime = System.currentTimeMillis()
            }

            override fun onUp(hgDialInfo: HGDialInfo?) {
                hgDialInfo ?: return

                jogWheelDial.doManualTextureDial(0.0)
                onJogWheeMovedByAngle(hgDialInfo, true)
                onMoveTime = 0L
                lastOnMoveTime = 0L
                lastAngle = 0.0
            }

            override fun onMove(hgDialInfo: HGDialInfo?) {
                hgDialInfo ?: return

                lastOnMoveTime = onMoveTime
                onMoveTime = System.currentTimeMillis()

                when (preferencesDataSource.getJogWheelModeEnum()) {
                    JogwheelMode.SPEED_ANGULAR -> onJogWheeMovedByAngle(hgDialInfo, false)
                    JogwheelMode.SPEED_VELOCITY -> onJogWheeMovedByVelocity(hgDialInfo, false)
                    else -> { Timber.w("onMove(): ELSE ") }
                }
            }
        })
    }

    private fun onJogWheeMovedByAngle(hgDialInfo: HGDialInfo, reset: Boolean) {

        var angle = 0.0
        if (!reset) angle = (hgDialInfo.textureAngle * 100)

        val delta = (angle * jogwheelSensitivity)

        val new = (currentPitchProgress + delta).toInt()
        playerViewModel.onPitchChanged(new)
        pitchControl.setNewProgress(new, false)
    }

    private fun onJogWheeMovedByVelocity(hgDialInfo: HGDialInfo, reset: Boolean) {

        var currentVelocity = 0.0
        val timeDiff = onMoveTime - lastOnMoveTime
        val angleDiff = hgDialInfo.textureAngle - lastAngle

        if (!reset) currentVelocity = angleDiff / timeDiff * 20000

        val delta = smoothenDelta(currentVelocity * jogwheelSensitivity)
        val new = (currentPitchProgress + delta).toInt()

        playerViewModel.onPitchChanged(new)
        pitchControl.setNewProgress(new, false)

        lastAngle = hgDialInfo.textureAngle
    }

    private fun smoothenDelta(delta: Double): Double {

        if (deltaList.size == 0) {
            deltaList.add(delta)
            return delta
        }

        var average = 0.0
        if (deltaList.size >= 10) deltaList.removeAt(0)
        deltaList.add(delta)
        deltaList.forEach {
            average += it
        }

        return average / deltaList.size
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
                this, R.layout.spinner_pitch_range, resources.getStringArray(R.array.pitch_range_values))
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_pitch_range)
        pitchRangeSpinner.adapter = spinnerAdapter
        pitchRangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                playerViewModel.onPitchRangeSelected(position)
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

        FirebaseCrashlytics
                .getInstance()
                .setCrashlyticsCollectionEnabled(preferencesDataSource.isCrashreportingEnabled())
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

    private fun startRemainBlinkAnimation(period: Long) {

        if (!isBlinkAnimationRunning) {
            Timber.d("startRemainBlinkAnimation():")

            remainBlinkAnimation.apply {
                duration = period
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
                start()
            }
            dynamicTrackLength.animation = remainBlinkAnimation
            trackProgressBar.animation = remainBlinkAnimation
            isBlinkAnimationRunning = true
        }
    }

    private fun stopRemainBlinkAnimation() {

        if (isBlinkAnimationRunning) {
            Timber.d("stopRemainBlinkAnimation():")

            dynamicTrackLength.clearAnimation()
            trackProgressBar.clearAnimation()
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
                    setPositiveButton(getString(R.string.dialog_crashlytics_opt_in_go_to_settings)) { dialog, _ ->
                        startSettingsActivity()
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.dialog_crashlytics_opt_in_no_thanks)) { dialog, _ ->
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

        override fun getPageTitle(position: Int): String? =
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
