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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.qstuff.qplayer.R
import org.qstuff.qplayer.ui.player.jogwheel.JogWheel
import org.qstuff.qplayer.ui.theme.QOrange
import org.qstuff.qplayer.util.JogwheelMode

/**
 * The upper deck: a vertical pitch fader on the left and the square jog wheel filling the width
 * to its right (so both share the same top edge and height). Both feed a single pitch value.
 *
 * @param pitchProgress current pitch (0..1000, 500 = neutral).
 * @param onPitchChange emit a new pitch value (parent updates its state + the player).
 * @param getSensitivity / getJogwheelMode read at gesture time so live setting changes apply.
 */
@Composable
fun PitchJogSection(
    pitchProgress: Int,
    onPitchChange: (Int) -> Unit,
    getSensitivity: () -> Int,
    getJogwheelMode: () -> JogwheelMode,
    modifier: Modifier = Modifier
) {
    val roundedShape = RoundedCornerShape(dimensionResource(R.dimen.rounded_shape_radius))
    val jogState = remember {
        object {
            var capturedPitchProgress = 500
            var onMoveTime = 0L
            var lastOnMoveTime = 0L
            var lastAngle = 0.0
            val deltaList = arrayListOf<Double>()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)   // 4dp below the duration row
    ) {
        val pitchbarWidth = dimensionResource(R.dimen.pitchbar_width)
        // The jog wheel is square and fills the width to the right of the pitch fader, so the
        // row height = that width. The pitch fader uses fillMaxHeight for the same top/height.
        val jogSize = maxWidth - pitchbarWidth
        Row(
            modifier = Modifier.fillMaxWidth().height(jogSize),
            verticalAlignment = Alignment.Top
        ) {
            // Pitch fader
            Box(
                modifier = Modifier
                    .width(pitchbarWidth)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
                    .background(Color.Black, roundedShape)
            ) {
                // Opaque orange fill from the centre (0%) out to the thumb + a QOrange playhead
                // line. Top = max (+range), bottom = min (-range).
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(roundedShape)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                fun emit(y: Float) {
                                    val v = ((1f - y / size.height) * 1000f).toInt().coerceIn(0, 1000)
                                    onPitchChange(v)
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

            // Jog wheel — square (aspectRatio inside), same height/top as the pitch fader
            JogWheel(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(Color.Black),
                onDown = {
                    jogState.capturedPitchProgress = pitchProgress
                    jogState.onMoveTime = System.currentTimeMillis()
                },
                onMove = { textureAngle ->
                    val sensitivity = getSensitivity()
                    jogState.lastOnMoveTime = jogState.onMoveTime
                    jogState.onMoveTime = System.currentTimeMillis()
                    when (getJogwheelMode()) {
                        JogwheelMode.SPEED_ANGULAR -> {
                            val delta = textureAngle * 100 * sensitivity
                            onPitchChange((jogState.capturedPitchProgress + delta).toInt())
                        }
                        JogwheelMode.SPEED_VELOCITY -> {
                            val timeDiff = jogState.onMoveTime - jogState.lastOnMoveTime
                            val angleDiff = textureAngle - jogState.lastAngle
                            val v = angleDiff / timeDiff * 20000
                            val delta = smoothenDelta(v * sensitivity, jogState.deltaList)
                            onPitchChange((jogState.capturedPitchProgress + delta).toInt())
                            jogState.lastAngle = textureAngle
                        }
                        else -> {}
                    }
                },
                onUp = {
                    onPitchChange(jogState.capturedPitchProgress)
                    jogState.onMoveTime = 0L
                    jogState.lastOnMoveTime = 0L
                    jogState.lastAngle = 0.0
                }
            )
        }
    }
}

private fun smoothenDelta(delta: Double, list: ArrayList<Double>): Double {
    if (list.isEmpty()) { list.add(delta); return delta }
    if (list.size >= 10) list.removeAt(0)
    list.add(delta)
    return list.sum() / list.size
}
