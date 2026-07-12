package org.qstuff.qplayer.player

import android.annotation.SuppressLint
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import org.koin.java.KoinJavaComponent.getKoin
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.filebrowser.FileBrowserScreen
import org.qstuff.qplayer.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.player.jogwheel.JogWheel
import org.qstuff.qplayer.player.trackprogress.CuepointView
import org.qstuff.qplayer.player.trackprogress.WaveformView
import org.qstuff.qplayer.playlists.PlaylistScreen
import org.qstuff.qplayer.playlists.PlaylistViewModel
import org.qstuff.qplayer.queue.QueueScreen
import org.qstuff.qplayer.queue.QueueViewModel
import org.qstuff.qplayer.ui.theme.QOrange
import org.qstuff.qplayer.util.JogwheelMode
import org.qstuff.qplayer.util.PlayerStatus
import org.qstuff.qplayer.util.TrackRepeatStatus
import java.util.concurrent.TimeUnit

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

    val playerStatus by playerViewModel.playerStatus.observeAsState()
    val trackStatus by playerViewModel.trackStatusMediator.observeAsState()
    val trackPosition by playerViewModel.onTrackPositionUpdate.observeAsState()
    val waveformData by playerViewModel.onWaveformDataUpdate.observeAsState()
    val masterTempo by playerViewModel.masterTempo.observeAsState()
    val pitchValueText by playerViewModel.pitchValueText.observeAsState()
    val pitchValue by playerViewModel.pitchValue.observeAsState()
    val pitchFactorIndex by playerViewModel.pitchFactorIndex.observeAsState()
    val cueActive by playerViewModel.cueActive.observeAsState()
    val showRemainingTime by playerViewModel.showRemainingTime.observeAsState()
    val repeat by queueViewModel.repeat.observeAsState()
    val shuffle by queueViewModel.shuffle.observeAsState()

    val pitchProgressState = remember { mutableStateOf(pitchValue ?: 500) }
    var pitchProgress by pitchProgressState

    var isTrackPrepared by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var isBlinkActive by remember { mutableStateOf(false) }
    // Progress uses two separate states so the position timer and the user's drag never
    // fight over one value: `playbackProgress` is written only by the timer, `seekProgress`
    // only by the drag. The Slider shows `seekProgress ?: playbackProgress`, so while
    // dragging the timer's updates aren't even read (short-circuit) — no thumb jump, and
    // no per-tick Slider recomposition to delay touch.
    var playbackProgress by remember { mutableStateOf(0f) }
    var seekProgress by remember { mutableStateOf<Float?>(null) }
    val displayedProgress = seekProgress ?: playbackProgress
    var cueProgressPos by remember { mutableStateOf(0) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPitchRangeMenu by remember { mutableStateOf(false) }

    val jogState = remember {
        object {
            var capturedPitchProgress = 500
            var onMoveTime = 0L
            var lastOnMoveTime = 0L
            var lastAngle = 0.0
            val deltaList = arrayListOf<Double>()
        }
    }

    LaunchedEffect(pitchValue) { pitchProgressState.value = pitchValue ?: 500 }

    LaunchedEffect(trackStatus) {
        trackStatus?.let { track ->
            isTrackPrepared = false
            currentTrack = track
            when (track.trackStatus) {
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
                playbackProgress = (trackPosition ?: 0L).toFloat() / track.duration
                isBlinkActive = (track.duration - (trackPosition ?: 0L)) in 0L..30000L
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
        val pos = trackPosition ?: 0L
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
    val isWaveformLoading = currentTrack?.trackStatus == Track.TrackStatus.LOADING
            || currentTrack?.trackStatus == Track.TrackStatus.UNDEFINED
    val trackTitleText = when {
        currentTrack == null -> ""
        currentTrack!!.trackStatus == Track.TrackStatus.LOADING
                || currentTrack!!.trackStatus == Track.TrackStatus.UNDEFINED -> "lade…"
        else -> currentTrack!!.name
    }

    val pitchRangeValues = stringArrayResource(R.array.pitch_range_values)
    val roundedShape = RoundedCornerShape(dimensionResource(R.dimen.rounded_shape_radius))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        val colHPadding = 4.dp
        val colWidth = maxWidth - colHPadding * 2
        val jogBorder = dimensionResource(R.dimen.jog_wheel_border)
        val pitchbarWidth = dimensionResource(R.dimen.pitchbar_width)
        val jogMarginRight = dimensionResource(R.dimen.jog_wheel_margin_right)
        val titleBarHeight = dimensionResource(R.dimen.title_textview_height)
        val textviewHeight = dimensionResource(R.dimen.textview_height)
        val seekbarHeight = dimensionResource(R.dimen.seekbar_height)

        // rowHeight mirrors the inner BoxWithConstraints formula for the jog+pitch row
        val rowHeight = colWidth - pitchbarWidth - colHPadding - jogMarginRight + jogBorder
        val jogSectionHeight = jogBorder + rowHeight
        val trackInfoHeight = textviewHeight * 2 + 4.dp
        val btnRowHeight = maxOf(48.dp, textviewHeight) + 4.dp
        val peekHeight = (maxHeight - titleBarHeight - jogSectionHeight - trackInfoHeight - btnRowHeight * 2 - seekbarHeight).coerceAtLeast(48.dp)
        val expandedHeight = (maxHeight - titleBarHeight - jogSectionHeight).coerceAtLeast(peekHeight)

        // Two-state sheet. `sheetOffset` measures how far it is collapsed:
        // 0 = expanded (top below the jog wheel), maxSheetOffset = collapsed (top below
        // the seekbar). The sheet's *height* is expandedHeight - sheetOffset, i.e. it
        // grows/shrinks rather than translating — so the tab content's LazyColumn viewport
        // always matches the visible area and short lists overflow & scroll when collapsed.
        // Dragging is bound ONLY to the TabRow (see below), so the lists scroll freely.
        val density = LocalDensity.current
        val expandedHeightPx = with(density) { expandedHeight.toPx() }
        val maxSheetOffsetPx = with(density) { (expandedHeight - peekHeight).toPx() }
        val sheetOffset = remember { Animatable(maxSheetOffsetPx) }
        val sheetScope = rememberCoroutineScope()

        // ─── Main player content ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = colHPadding)
        ) {

            // ─── Title bar ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.title_textview_height))
                    .background(Color.Black, roundedShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = QOrange)) { append("q") }
                        withStyle(SpanStyle(color = Color.White)) { append("deq") }
                        if (titleSuffix.isNotEmpty()) append(titleSuffix)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = dimensionResource(R.dimen.textview_padding_start))
                )
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(dimensionResource(R.dimen.kebabbutton_size))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert_white_24dp),
                            contentDescription = "More",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { showMoreMenu = false; onOpenSettings() }
                        )
                        DropdownMenuItem(
                            text = { Text("Privacy") },
                            onClick = { showMoreMenu = false; onOpenWebView("privacy.html") }
                        )
                        DropdownMenuItem(
                            text = { Text("Imprint") },
                            onClick = { showMoreMenu = false; onOpenWebView("imprint.html") }
                        )
                        DropdownMenuItem(
                            text = { Text("Licenses") },
                            onClick = { showMoreMenu = false; onOpenWebView("licenses.html") }
                        )
                    }
                }
            }

            // ─── Seekbar + Waveform ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.seekbar_height))
                    .padding(vertical = 4.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WaveformView(ctx).apply { updateWaveform(null) }
                    },
                    update = { view ->
                        val td = waveformData
                        when {
                            td != null && td.track == currentTrack -> view.updateWaveform(td)
                            isWaveformLoading -> view.updateWaveform(null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black, roundedShape)
                        .padding(horizontal = dimensionResource(R.dimen.rounded_shape_radius))
                )
                if (isWaveformLoading) {
                    Text(
                        text = "calculating waveform data…",
                        color = QOrange.copy(alpha = 0.67f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                // Progress: translucent orange fill over the played portion + a full-height
                // white playhead line. Drag/tap to seek. seekProgress (drag) short-circuits
                // playbackProgress (timer) so the two never fight — see the state decl above.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(roundedShape)
                        .graphicsLayer { alpha = dynamicTimeAlpha }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                seekProgress = (down.position.x / size.width).coerceIn(0f, 1f)
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.first()
                                    seekProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                                    change.consume()
                                } while (event.changes.any { it.pressed })
                                seekProgress?.let { target ->
                                    playbackProgress = target // avoid a 1-frame jump back
                                    currentTrack?.let { track ->
                                        playerViewModel.seekTo((target * track.duration).toDouble(), false)
                                    }
                                }
                                seekProgress = null
                            }
                        }
                ) {
                    val x = displayedProgress.coerceIn(0f, 1f) * size.width
                    drawRect(
                        color = QOrange.copy(alpha = 0.35f),
                        size = Size(x, size.height)
                    )
                    drawLine(
                        color = QOrange,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                AndroidView(
                    factory = { ctx -> CuepointView(ctx) },
                    update = { view ->
                        view.cuepointPosition = cueProgressPos
                        view.visibility = if (cueActive == true) View.VISIBLE else View.INVISIBLE
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ─── Track info ───────────────────────────────────────────────────
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = trackTitleText,
                    color = QOrange,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(R.dimen.textview_height))
                        .background(Color.Black)
                        .padding(start = dimensionResource(R.dimen.textview_padding_start))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(R.dimen.textview_height))
                        .background(Color.Black, roundedShape),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = totalDurationText,
                        color = Color.White,
                        modifier = Modifier.padding(start = dimensionResource(R.dimen.textview_padding_start))
                    )
                    Text(
                        text = dynamicTimeText,
                        color = QOrange,
                        modifier = Modifier
                            .padding(end = dimensionResource(R.dimen.textview_padding_end))
                            .graphicsLayer { alpha = dynamicTimeAlpha }
                            .clickable { playerViewModel.toggleDynamicTrackLengthDisplay() }
                    )
                }
            }

            // ─── Upper section: pitch fader + jog wheel ──────────────────────
            // BoxWithConstraints lets us derive Row height from the wheel's square size:
            // rowHeight = (availableWidth - pitchbarWidth - paddingStart - paddingEnd) + paddingBottom
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(R.dimen.jog_wheel_border))
            ) {
                val pitchbarWidth = dimensionResource(R.dimen.pitchbar_width)
                val jogPaddingStart = 4.dp
                val jogPaddingEnd = dimensionResource(R.dimen.jog_wheel_margin_right)
                val jogPaddingBottom = dimensionResource(R.dimen.jog_wheel_border)
                val rowHeight = maxWidth - pitchbarWidth - jogPaddingStart - jogPaddingEnd + jogPaddingBottom
                Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
                // Pitch fader
                Box(
                    modifier = Modifier
                        .width(pitchbarWidth)
                        .fillMaxHeight()
                        .padding(vertical = 10.dp)
                        .background(Color.Black, roundedShape)
                ) {
                    // Vertical pitch fader: opaque orange fill from the centre (0%) out to the
                    // thumb + a QOrange playhead line. Top = max (+range), bottom = min (-range).
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(roundedShape)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    fun emit(y: Float) {
                                        val v = ((1f - y / size.height) * 1000f).toInt().coerceIn(0, 1000)
                                        pitchProgressState.value = v
                                        playerViewModel.onPitchChanged(v)
                                    }
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    emit(down.position.y)
                                    down.consume()
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.first()
                                        emit(change.position.y)
                                        change.consume()
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                    ) {
                        val fraction = (pitchProgress / 1000f).coerceIn(0f, 1f)
                        val thumbY = (1f - fraction) * size.height
                        val centerY = size.height / 2f
                        drawRect(
                            color = QOrange,
                            topLeft = Offset(0f, minOf(thumbY, centerY)),
                            size = Size(size.width, abs(thumbY - centerY))
                        )
                        drawLine(
                            color = QOrange,
                            start = Offset(0f, thumbY),
                            end = Offset(size.width, thumbY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Jog wheel — fills remaining width, square via aspectRatio inside
                JogWheel(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = jogPaddingStart,
                            end = jogPaddingEnd,
                            bottom = jogPaddingBottom
                        ),
                    onDown = {
                        jogState.capturedPitchProgress = pitchProgressState.value
                        jogState.onMoveTime = System.currentTimeMillis()
                    },
                    onMove = { textureAngle ->
                        val sensitivity = playerViewModel.jogwheelSensitivity.value ?: 10
                        jogState.lastOnMoveTime = jogState.onMoveTime
                        jogState.onMoveTime = System.currentTimeMillis()
                        when (preferencesDataSource.getJogWheelModeEnum()) {
                            JogwheelMode.SPEED_ANGULAR -> {
                                val delta = textureAngle * 100 * sensitivity
                                val new = (jogState.capturedPitchProgress + delta).toInt()
                                pitchProgressState.value = new
                                playerViewModel.onPitchChanged(new)
                            }
                            JogwheelMode.SPEED_VELOCITY -> {
                                val timeDiff = jogState.onMoveTime - jogState.lastOnMoveTime
                                val angleDiff = textureAngle - jogState.lastAngle
                                val v = angleDiff / timeDiff * 20000
                                val delta = smoothenDelta(v * sensitivity, jogState.deltaList)
                                val new = (jogState.capturedPitchProgress + delta).toInt()
                                pitchProgressState.value = new
                                playerViewModel.onPitchChanged(new)
                                jogState.lastAngle = textureAngle
                            }
                            else -> {}
                        }
                    },
                    onUp = {
                        pitchProgressState.value = jogState.capturedPitchProgress
                        playerViewModel.onPitchChanged(jogState.capturedPitchProgress)
                        jogState.onMoveTime = 0L
                        jogState.lastOnMoveTime = 0L
                        jogState.lastAngle = 0.0
                    }
                )
                } // Row
            } // BoxWithConstraints

            // ─── Button Row 1: prev | play | next | cue | mt | reset ─────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { queueViewModel.previousTrack(currentTrack) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.button_previous),
                        contentDescription = "Previous",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { playerViewModel.playPause() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(
                            if (playerStatus == PlayerStatus.PLAYING)
                                R.drawable.button_pause_selected
                            else
                                R.drawable.button_play_selected
                        ),
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { queueViewModel.nextTrack(currentTrack) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.button_next),
                        contentDescription = "Next",
                        tint = Color.White
                    )
                }
                // Cue
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dimensionResource(R.dimen.textview_height))
                        .background(Color.Black, roundedShape)
                        .combinedClickable(
                            onClick = {
                                if (cueActive == true) {
                                    playerViewModel.playFromCue(currentTrack)
                                } else {
                                    currentTrack?.also {
                                        cueProgressPos = (displayedProgress * 1000).toInt()
                                        playerViewModel.toggleCue(it, true)
                                    }
                                }
                            },
                            onLongClick = {
                                if (cueActive == true) {
                                    currentTrack?.also {
                                        playerViewModel.toggleCue(it, false)
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "cue",
                        color = if (cueActive == true) QOrange else Color.White
                    )
                }
                // Master tempo
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dimensionResource(R.dimen.textview_height))
                        .background(Color.Black, roundedShape)
                        .clickable {
                            playerViewModel.toggleMasterTempo()
                            playerViewModel.onPitchChanged(pitchProgress)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "mt",
                        color = if (masterTempo == true) QOrange else Color.White
                    )
                }
                // Reset pitch
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dimensionResource(R.dimen.textview_height))
                        .background(Color.Black, roundedShape)
                        .clickable {
                            pitchProgressState.value = 500
                            playerViewModel.onPitchChanged(500)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "reset", color = Color.White)
                }
            }

            // ─── Button Row 2: pitch- | value | pitch+ | range | repeat | shuffle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pitch decrease
                IconButton(
                    onClick = {
                        val delta = (0.1f * playerViewModel.pitchFactor).toInt()
                        val new = pitchProgress - delta
                        pitchProgressState.value = new
                        playerViewModel.onPitchChanged(new)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_arrow_left_white_24px),
                        contentDescription = "Pitch decrease",
                        tint = Color.White
                    )
                }
                // Pitch value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dimensionResource(R.dimen.textview_height))
                        .background(Color.Black, roundedShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = pitchValueText ?: "0,0%", color = Color.White)
                }
                // Pitch increase
                IconButton(
                    onClick = {
                        val delta = (0.1f * playerViewModel.pitchFactor).toInt()
                        val new = pitchProgress + delta
                        pitchProgressState.value = new
                        playerViewModel.onPitchChanged(new)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_arrow_right_white_24px),
                        contentDescription = "Pitch increase",
                        tint = Color.White
                    )
                }
                // Pitch range
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dimensionResource(R.dimen.textview_height))
                        .background(Color.Black, roundedShape)
                        .clickable { showPitchRangeMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pitchRangeValues.getOrElse(pitchFactorIndex ?: 0) { "?" },
                        color = Color.White
                    )
                    DropdownMenu(
                        expanded = showPitchRangeMenu,
                        onDismissRequest = { showPitchRangeMenu = false }
                    ) {
                        pitchRangeValues.forEachIndexed { index, value ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = {
                                    playerViewModel.onPitchRangeSelected(index)
                                    showPitchRangeMenu = false
                                }
                            )
                        }
                    }
                }
                // Repeat
                IconButton(
                    onClick = { queueViewModel.toggleRepeat() },
                    modifier = Modifier.weight(1f)
                ) {
                    val repeatIcon = when (repeat) {
                        TrackRepeatStatus.ONE -> R.drawable.button_loop1_selected
                        TrackRepeatStatus.ALL -> R.drawable.button_loop_selected
                        else -> R.drawable.button_loop
                    }
                    Icon(
                        painter = painterResource(repeatIcon),
                        contentDescription = "Repeat",
                        tint = Color.White
                    )
                }
                // Shuffle
                IconButton(
                    onClick = { queueViewModel.toggleShuffle() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(
                            if (shuffle == true) R.drawable.button_shuffle_selected
                            else R.drawable.button_shuffle
                        ),
                        contentDescription = "Shuffle",
                        tint = Color.White
                    )
                }
            }

        }

        // ─── Bottom sheet (tabs) — draggable ONLY via the TabRow ─────────────
        val pagerState = rememberPagerState(pageCount = { 3 })
        val tabTitles = listOf(
            stringResource(R.string.queue_title),
            stringResource(R.string.filebrowser_title),
            stringResource(R.string.playlists_title)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Height = expandedHeight - sheetOffset, forced in the layout phase so the
                // drag only re-lays-out (no recomposition). Reading sheetOffset here makes
                // the list viewport track the visible height → short lists scroll too.
                .layout { measurable, constraints ->
                    val h = (expandedHeightPx - sheetOffset.value).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(placeable.width, h) { placeable.place(0, 0) }
                }
                .background(Color.Black)
                // Block taps from reaching the buttons/content behind the sheet when
                // it's expanded (mirrors Material Surface's pointerInput(Unit) {}).
                .pointerInput(Unit) {}
        ) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Black,
                contentColor = QOrange,
                modifier = Modifier.draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        sheetScope.launch {
                            sheetOffset.snapTo(
                                (sheetOffset.value + delta).coerceIn(0f, maxSheetOffsetPx)
                            )
                        }
                    },
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity > 800f -> maxSheetOffsetPx   // fling down → collapse
                            velocity < -800f -> 0f                // fling up → expand
                            sheetOffset.value > maxSheetOffsetPx / 2 -> maxSheetOffsetPx
                            else -> 0f
                        }
                        sheetScope.launch { sheetOffset.animateTo(target, tween(300)) }
                    }
                )
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            if (pagerState.currentPage == index) {
                                // tap on the already-selected tab → toggle the sheet
                                val target =
                                    if (sheetOffset.value < maxSheetOffsetPx / 2) maxSheetOffsetPx
                                    else 0f
                                sheetScope.launch { sheetOffset.animateTo(target, tween(300)) }
                            } else {
                                sheetScope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        text = { Text(title) },
                        selectedContentColor = QOrange,
                        unselectedContentColor = Color.White
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondBoundsPageCount = 1
            ) { page ->
                when (page) {
                    0 -> QueueScreen(
                        queueViewModel = queueViewModel,
                        playlistViewModel = playlistViewModel
                    )
                    1 -> FileBrowserScreen(
                        fileBrowserViewModel = fileBrowserViewModel,
                        queueViewModel = queueViewModel,
                        playerViewModel = playerViewModel
                    )
                    2 -> PlaylistScreen(
                        playlistViewModel = playlistViewModel,
                        queueViewModel = queueViewModel
                    )
                }
            }
        }
    }
}

private fun smoothenDelta(delta: Double, list: ArrayList<Double>): Double {
    if (list.isEmpty()) { list.add(delta); return delta }
    if (list.size >= 10) list.removeAt(0)
    list.add(delta)
    return list.sum() / list.size
}

private fun getDurationHumanReadable(time: Long) =
    String.format(
        "%02d:%02d:%02d",
        TimeUnit.MILLISECONDS.toHours(time),
        TimeUnit.MILLISECONDS.toMinutes(time) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)),
        TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))
    )
