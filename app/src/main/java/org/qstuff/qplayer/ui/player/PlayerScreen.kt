package org.qstuff.qplayer.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import org.koin.java.KoinJavaComponent.getKoin
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.player.components.PitchJogSection
import org.qstuff.qplayer.ui.player.components.PlayerControls
import org.qstuff.qplayer.ui.player.components.PlayerTabSheet
import org.qstuff.qplayer.ui.player.components.PlayerTitleBar
import org.qstuff.qplayer.ui.player.components.TrackInfoSection
import org.qstuff.qplayer.ui.player.components.WaveformSeekbar
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueViewModel
import org.qstuff.qplayer.util.PlayerStatus
import java.util.concurrent.TimeUnit

/**
 * The player screen. This composable owns all the hoisted state (observers, remembered values
 * and the track-status / position effects) and lays out the six UI sections, which live in
 * [org.qstuff.qplayer.ui.player.components]: title bar, track info, waveform seekbar, the
 * pitch/jog deck, the control buttons, and the draggable tab sheet.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    queueViewModel: QueueViewModel,
    playlistViewModel: PlaylistViewModel,
    fileBrowserViewModel: FileBrowserViewModel,
    titleSuffix: String,
    onOpenSettings: () -> Unit,
    onOpenWebView: (url: String) -> Unit
) {
    val preferencesDataSource: PreferencesDataSource = remember { getKoin().get() }

    val playerStatus by playerViewModel.playerStatus.collectAsStateWithLifecycle()
    // Current track + status as an immutable snapshot. Its `revision` makes every emission
    // distinct, so re-selecting the same track still recomposes and re-runs the status effect —
    // Track.trackStatus is @Ignore and the media layer mutates the same Track in place, which
    // plain equality dedup would otherwise swallow. See PlayerTrackState.
    val playerTrackState by playerViewModel.playerTrackState.collectAsStateWithLifecycle()
    val currentTrack = playerTrackState?.track
    val trackPosition by playerViewModel.onTrackPositionUpdate.collectAsStateWithLifecycle()
    // onWaveformDataUpdate is still LiveData (bridged from the media service) — Phase 2.
    val waveformData by playerViewModel.onWaveformDataUpdate.observeAsState()
    val masterTempo by playerViewModel.masterTempo.collectAsStateWithLifecycle()
    val pitchValueText by playerViewModel.pitchValueText.collectAsStateWithLifecycle()
    val pitchValue by playerViewModel.pitchValue.collectAsStateWithLifecycle()
    val pitchFactorIndex by playerViewModel.pitchFactorIndex.collectAsStateWithLifecycle()
    val cueActive by playerViewModel.cueActive.collectAsStateWithLifecycle()
    val showRemainingTime by playerViewModel.showRemainingTime.collectAsStateWithLifecycle()
    val repeat by queueViewModel.repeat.observeAsState()
    val shuffle by queueViewModel.shuffle.observeAsState()

    val pitchProgressState = remember { mutableStateOf(pitchValue) }
    var pitchProgress by pitchProgressState

    var isTrackPrepared by remember { mutableStateOf(false) }
    var isBlinkActive by remember { mutableStateOf(false) }
    // Progress uses two separate states so the position timer and the user's drag never
    // fight over one value: `playbackProgress` is written only by the timer, `seekProgress`
    // only by the drag. The seekbar shows `seekProgress ?: playbackProgress`, so while
    // dragging the timer's updates aren't even read (short-circuit) — no thumb jump.
    var playbackProgress by remember { mutableStateOf(0f) }
    var seekProgress by remember { mutableStateOf<Float?>(null) }
    val displayedProgress = seekProgress ?: playbackProgress
    var cueProgressPos by remember { mutableStateOf(0) }

    LaunchedEffect(pitchValue) { pitchProgressState.value = pitchValue }

    LaunchedEffect(playerTrackState) {
        playerTrackState?.let { state ->
            val track = state.track
            isTrackPrepared = false
            when (state.status) {
                Track.TrackStatus.LOADING, Track.TrackStatus.UNDEFINED -> {
                    isBlinkActive = false
                    playbackProgress = 0f
                    seekProgress = null
                }
                Track.TrackStatus.PREPARED -> {
                    isTrackPrepared = true
                    isBlinkActive = false
                    playbackProgress = 0f
                    seekProgress = null
                    playerViewModel.seekTo(track.playPosition.toDouble(), track.isAutoplay)
                    if (track.isAutoplay) playerViewModel.playPause()
                }
                Track.TrackStatus.COMPLETED -> {
                    isBlinkActive = false
                    playerViewModel.onTrackCompleted(track)
                    queueViewModel.onTrackCompleted(track)
                    track.playPosition = 0
                }
                Track.TrackStatus.ERROR -> {}
            }
        }
    }

    LaunchedEffect(trackPosition) {
        currentTrack?.let { track ->
            if (track.duration > 0) {
                playbackProgress = trackPosition.toFloat() / track.duration
                isBlinkActive = (track.duration - trackPosition) in 0L..30000L
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "blinkAlpha"
    )
    val dynamicTimeAlpha = if (isBlinkActive) blinkAlpha else 1.0f

    // Key on duration (not the Track reference): duration is mutated in place on the same
    // Track object once the player is ready, so keying on `currentTrack` never recomputes.
    val totalDurationText = remember(currentTrack?.duration) {
        currentTrack?.let { "total: ${getDurationHumanReadable(it.duration)}" } ?: ""
    }
    val dynamicTimeText = remember(trackPosition, currentTrack, showRemainingTime) {
        val pos = trackPosition
        val track = currentTrack
        if (track != null && track.duration > 0) {
            if (showRemainingTime != false) {
                "remain: ${getDurationHumanReadable(track.duration - pos)}"
            } else {
                "current: ${getDurationHumanReadable(pos)}"
            }
        } else {
            "remain: 00:00:00"
        }
    }
    // The overview waveform is generated asynchronously; it's "ready" only once data for the
    // current track has arrived. Until then we show the calculating indicator.
    val waveformReady = waveformData?.let { it.bytes != null && it.track == currentTrack } == true
    val trackTitleText = when {
        currentTrack == null -> ""
        playerTrackState?.status == Track.TrackStatus.LOADING
                || playerTrackState?.status == Track.TrackStatus.UNDEFINED -> "lade…"
        else -> currentTrack.name
    }

    val pitchRangeValues = stringArrayResource(R.array.pitch_range_values)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        val colHPadding = 4.dp
        val colWidth = maxWidth - colHPadding * 2
        val pitchbarWidth = dimensionResource(R.dimen.pitchbar_width)
        val titleBarHeight = dimensionResource(R.dimen.title_textview_height)
        val textviewHeight = dimensionResource(R.dimen.textview_height)
        val seekbarHeight = dimensionResource(R.dimen.seekbar_height)

        // Jog+pitch row: the jog wheel is a square filling the width to the right of the
        // pitch fader, so its height = that width. +4.dp gap below the duration row.
        // Mirrors PitchJogSection's inner BoxWithConstraints.
        val jogSectionHeight = 4.dp + (colWidth - pitchbarWidth)
        val trackInfoHeight = textviewHeight * 2 + 4.dp
        // Two button rows, each textviewHeight tall, with 8dp above row1 / between / below row2.
        val btnRowsHeight = textviewHeight * 2 + 24.dp
        // -8.dp leaves a visible white gap between the button row and the collapsed panel top.
        val peekHeight = (maxHeight - titleBarHeight - jogSectionHeight - trackInfoHeight - btnRowsHeight - seekbarHeight - 8.dp).coerceAtLeast(48.dp)
        val expandedHeight = (maxHeight - titleBarHeight - jogSectionHeight).coerceAtLeast(peekHeight)

        val density = LocalDensity.current
        val expandedHeightPx = with(density) { expandedHeight.toPx() }
        val maxSheetOffsetPx = with(density) { (expandedHeight - peekHeight).toPx() }

        // ─── Main player content ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = colHPadding)
        ) {
            PlayerTitleBar(
                titleSuffix = titleSuffix,
                onOpenSettings = onOpenSettings,
                onOpenWebView = onOpenWebView,
                modifier = Modifier.padding(top = 4.dp)
            )

            TrackInfoSection(
                trackTitleText = trackTitleText,
                totalDurationText = totalDurationText,
                dynamicTimeText = dynamicTimeText,
                dynamicTimeAlpha = dynamicTimeAlpha,
                onToggleTimeDisplay = { playerViewModel.toggleDynamicTrackLengthDisplay() }
            )

            WaveformSeekbar(
                waveformData = waveformData,
                waveformReady = waveformReady,
                showCalculating = currentTrack != null && !waveformReady,
                displayedProgress = displayedProgress,
                dynamicTimeAlpha = dynamicTimeAlpha,
                cueActive = cueActive == true,
                cueProgressPos = cueProgressPos,
                onSeekChange = { seekProgress = it },
                onSeekCommit = { target ->
                    playbackProgress = target // avoid a 1-frame jump back
                    currentTrack?.let { track ->
                        playerViewModel.seekTo((target * track.duration).toDouble(), false)
                    }
                    seekProgress = null
                }
            )

            PitchJogSection(
                pitchProgress = pitchProgress,
                onPitchChange = { v ->
                    pitchProgressState.value = v
                    playerViewModel.onPitchChanged(v)
                },
                getSensitivity = { playerViewModel.jogwheelSensitivity.value },
                getJogwheelMode = { preferencesDataSource.getJogWheelModeEnum() }
            )

            PlayerControls(
                isPlaying = playerStatus == PlayerStatus.PLAYING,
                repeat = repeat,
                shuffle = shuffle == true,
                cueActive = cueActive == true,
                masterTempo = masterTempo == true,
                pitchValueText = pitchValueText,
                pitchRangeValues = pitchRangeValues,
                pitchFactorIndex = pitchFactorIndex,
                onPrevious = { queueViewModel.previousTrack(currentTrack) },
                onPlayPause = { playerViewModel.playPause() },
                onNext = { queueViewModel.nextTrack(currentTrack) },
                onToggleRepeat = { queueViewModel.toggleRepeat() },
                onToggleShuffle = { queueViewModel.toggleShuffle() },
                onCueClick = {
                    if (cueActive == true) {
                        playerViewModel.playFromCue(currentTrack)
                    } else {
                        currentTrack?.also {
                            cueProgressPos = (displayedProgress * 1000).toInt()
                            playerViewModel.toggleCue(it, true)
                        }
                    }
                },
                onCueLongClick = {
                    if (cueActive == true) {
                        currentTrack?.also { playerViewModel.toggleCue(it, false) }
                    }
                },
                onPitchDecrease = {
                    val delta = (0.1f * playerViewModel.pitchFactor).toInt()
                    val new = pitchProgress - delta
                    pitchProgressState.value = new
                    playerViewModel.onPitchChanged(new)
                },
                onPitchIncrease = {
                    val delta = (0.1f * playerViewModel.pitchFactor).toInt()
                    val new = pitchProgress + delta
                    pitchProgressState.value = new
                    playerViewModel.onPitchChanged(new)
                },
                onPitchRangeSelected = { index -> playerViewModel.onPitchRangeSelected(index) },
                onReset = {
                    pitchProgressState.value = 500
                    playerViewModel.onPitchChanged(500)
                },
                onToggleMasterTempo = {
                    playerViewModel.toggleMasterTempo()
                    playerViewModel.onPitchChanged(pitchProgress)
                }
            )
        }

        PlayerTabSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
            expandedHeightPx = expandedHeightPx,
            maxSheetOffsetPx = maxSheetOffsetPx,
            queueViewModel = queueViewModel,
            playlistViewModel = playlistViewModel,
            fileBrowserViewModel = fileBrowserViewModel,
            playerViewModel = playerViewModel
        )
    }
}

private fun getDurationHumanReadable(time: Long) =
    String.format(
        "%02d:%02d:%02d",
        TimeUnit.MILLISECONDS.toHours(time),
        TimeUnit.MILLISECONDS.toMinutes(time) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)),
        TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))
    )
