package org.qstuff.qplayer.ui.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.time.TimeSource
import org.qstuff.qplayer.ui.player.jogwheel.JogWheel
import org.qstuff.qplayer.ui.theme.QOrange
import org.qstuff.qplayer.util.JogwheelMode
import org.qstuff.qplayer.ui.theme.LocalDimens
import org.qstuff.qplayer.ui.player.jogwheel.JogModeSwitch
import org.qstuff.qplayer.ui.player.jogwheel.JogSensitivitySwitch
import androidx.compose.foundation.layout.aspectRatio


/** Monotonic clock for the jog wheel's velocity mode (only time differences count). */
private val jogClock = TimeSource.Monotonic.markNow()

/**
 * Everything the jog wheel needs to drive the player — passed as one value, so both player
 * layouts (modern [PitchJogSection], classic deck) can place a [JogWheelControl].
 *
 * @param getPitchProgress the current pitch at the moment it's called — the jog wheel's base on
 *        touch-down (the composed pitch may still be the previous composition's value right
 *        after a release, which would make a bent speed the new base).
 * @param onPitchChange emit a new pitch value (0..1000, 500 = neutral).
 * @param getSensitivity / getJogwheelMode read at gesture time so live setting changes apply.
 * @param onScratchStart in SCRATCH mode, called on touch-down; returns whether scratching started
 *        (the gesture then drives [onScratch] with the rotation since touch-down and ends with
 *        [onScratchEnd]).
 * @param onJogTouch / onJogRotate every touch-down / rotation (since touch-down), in any mode —
 *        used for haptic feedback.
 */
class JogWheelActions(
    val getPitchProgress: () -> Int,
    val onPitchChange: (Int) -> Unit,
    val getSensitivity: () -> Int,
    val getJogwheelMode: () -> JogwheelMode,
    val onScratchStart: () -> Boolean,
    val onScratch: (rotationRad: Double) -> Unit,
    val onScratchEnd: () -> Unit,
    val onJogTouch: () -> Unit,
    val onJogRotate: (rotationRad: Double) -> Unit
)

/**
 * The upper deck of the modern layout: a vertical pitch fader on the left and the square jog
 * wheel filling the width to its right (so both share the same top edge and height), with the
 * mode / sensitivity quick switches in the jog square's top corners. Both feed one pitch value.
 *
 * @param pitchProgress current pitch (0..1000, 500 = neutral), for drawing the fader.
 * @param jogwheelMode / onJogwheelModeChange, jogwheelSensitivity / onJogwheelSensitivityChange
 *        the quick switches in the jog square's top corners (see JogQuickSwitches).
 * @param jogEnabled false: the jog wheel ignores touches (phone: while the tab sheet is open, so a
 *        swipe to collapse it can't hit the wheel by accident).
 * @param pitchbarWidth width of the pitch fader slot, incl. its 4dp gap to the jog wheel.
 * @param fitHeight false (phone): the jog wheel fills the width, the row's height follows.
 *        true (tablet pane): the jog wheel is also limited by the available height, and the
 *        fader + wheel group is centred horizontally.
 */
@Composable
fun PitchJogSection(
    pitchProgress: Int,
    jogActions: JogWheelActions,
    jogwheelMode: JogwheelMode,
    onJogwheelModeChange: (JogwheelMode) -> Unit,
    jogwheelSensitivity: Int,
    onJogwheelSensitivityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pitchbarWidth: Dp = LocalDimens.current.pitchbarWidth,
    fitHeight: Boolean = false,
    jogEnabled: Boolean = true
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),  // 4dp below the duration row
        contentAlignment = Alignment.TopCenter
    ) {
        // The jog wheel is square and fills the width to the right of the pitch fader, so the
        // row height = that width. The pitch fader uses fillMaxHeight for the same top/height.
        val jogSize = if (fitHeight) {
            minOf(maxWidth - pitchbarWidth, maxHeight)
        } else {
            maxWidth - pitchbarWidth
        }
        Row(
            modifier = Modifier.width(pitchbarWidth + jogSize).height(jogSize),
            verticalAlignment = Alignment.Top
        ) {
            PitchFader(
                pitchProgress = pitchProgress,
                onPitchChange = jogActions.onPitchChange,
                modifier = Modifier
                    .width(pitchbarWidth)
                    .fillMaxHeight()
                    .padding(end = ControlChipSpacing)
            )

            // Jog wheel — square, same height/top as the pitch fader. The square's top corners
            // (outside the wheel) hold the quick switches. Clipped to the shared rounded shape
            // like every other deck element.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(LocalDimens.current.roundedShapeRadius))
            ) {
                JogWheelControl(
                    actions = jogActions,
                    enabled = jogEnabled,
                    modifier = Modifier.fillMaxHeight().background(Color.Black)
                )
                JogModeSwitch(
                    mode = jogwheelMode,
                    onModeChange = onJogwheelModeChange,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                )
                JogSensitivitySwitch(
                    sensitivity = jogwheelSensitivity,
                    onSensitivityChange = onJogwheelSensitivityChange,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                )
            }
        }
    }
}

/**
 * Vertical pitch fader: a black rounded bar with an opaque orange fill from the centre (0 %) out
 * to the thumb, plus a playhead line. Top = max (+range), bottom = min (-range). Touch or drag
 * anywhere sets the pitch.
 */
@Composable
fun PitchFader(
    pitchProgress: Int,
    onPitchChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val roundedShape = RoundedCornerShape(LocalDimens.current.roundedShapeRadius)
    val currentOnPitchChange by rememberUpdatedState(onPitchChange)
    Box(modifier = modifier.background(Color.Black, roundedShape)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(roundedShape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        fun emit(y: Float) {
                            val v = ((1f - y / size.height) * 1000f).toInt().coerceIn(0, 1000)
                            currentOnPitchChange(v)
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
}

/**
 * The jog wheel with its pitch / scratch behaviour (see [JogWheelActions]): angular or velocity
 * pitch bend that snaps back on release, or scratching in SCRATCH mode.
 *
 * @param showQTail the orange "Q" stick from the centre to the bottom-right corner (modern);
 *        the classic layout shows only the wheel.
 */
@Composable
fun JogWheelControl(
    actions: JogWheelActions,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showQTail: Boolean = true
) {
    val handler = rememberJogWheelHandler(actions)
    JogWheel(
        modifier = modifier,
        enabled = enabled,
        showQTail = showQTail,
        onDown = handler::onDown,
        onMove = handler::onMove,
        onUp = handler::onUp
    )
}

/** [JogWheelHandler] for [actions], kept across recompositions. */
@Composable
fun rememberJogWheelHandler(actions: JogWheelActions): JogWheelHandler {
    val handler = remember { JogWheelHandler(actions) }
    handler.actions = actions
    return handler
}

/**
 * Turns jog wheel gestures (touch-down, rotation since touch-down, release) into pitch bends or
 * scratching — see [JogWheelControl], or the classic deck, which feeds it from its own touch area.
 */
class JogWheelHandler(var actions: JogWheelActions) {
    private var capturedPitchProgress = 500
    private var onMoveTime = 0L
    private var lastOnMoveTime = 0L
    private var lastAngle = 0.0
    private val deltaList = arrayListOf<Double>()
    private var scratching = false

    fun onDown() {
        actions.onJogTouch()
        capturedPitchProgress = actions.getPitchProgress()
        onMoveTime = jogClock.elapsedNow().inWholeMilliseconds
        lastAngle = 0.0
        deltaList.clear()
        scratching = actions.getJogwheelMode() == JogwheelMode.SCRATCH && actions.onScratchStart()
    }

    fun onMove(textureAngle: Double) {
        actions.onJogRotate(textureAngle)
        if (scratching) {
            actions.onScratch(textureAngle)
            return
        }
        val sensitivity = actions.getSensitivity()
        lastOnMoveTime = onMoveTime
        onMoveTime = jogClock.elapsedNow().inWholeMilliseconds
        when (actions.getJogwheelMode()) {
            JogwheelMode.SPEED_ANGULAR -> {
                val delta = textureAngle * 100 * sensitivity
                actions.onPitchChange((capturedPitchProgress + delta).toInt())
            }
            JogwheelMode.SPEED_VELOCITY -> {
                val timeDiff = onMoveTime - lastOnMoveTime
                // Two moves within the same millisecond: no velocity (avoids ÷ 0).
                if (timeDiff <= 0) return
                val angleDiff = textureAngle - lastAngle
                val v = angleDiff / timeDiff * 20000
                val delta = smoothenDelta(v * sensitivity, deltaList)
                actions.onPitchChange((capturedPitchProgress + delta).toInt())
                lastAngle = textureAngle
            }
            else -> {}
        }
    }

    fun onUp() {
        if (scratching) {
            scratching = false
            actions.onScratchEnd()
            return
        }
        actions.onPitchChange(capturedPitchProgress)
        onMoveTime = 0L
        lastOnMoveTime = 0L
        lastAngle = 0.0
    }
}

private fun smoothenDelta(delta: Double, list: ArrayList<Double>): Double {
    if (list.isEmpty()) { list.add(delta); return delta }
    if (list.size >= 10) list.removeAt(0)
    list.add(delta)
    return list.sum() / list.size
}
