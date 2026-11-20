package org.qstuff.qplayer.ui.player.jogwheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.State
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.qstuff.qplayer.ui.theme.QOrange
import kotlin.math.PI
import kotlin.math.atan2
import org.jetbrains.compose.resources.painterResource
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.qpl_btn_wheel_ohne_rand01

/**
 * The jog wheel: [jogWheelInput] on a [JogWheelFace]. Touches anywhere in its square turn it.
 */
@Composable
fun JogWheel(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 8.dp,
    enabled: Boolean = true,
    showQTail: Boolean = true,
    onDown: () -> Unit,
    onMove: (textureAngle: Double) -> Unit,
    onUp: () -> Unit
) {
    val rotationDeg = remember { mutableStateOf(0f) }
    JogWheelFace(
        rotationDeg = rotationDeg,
        borderWidth = borderWidth,
        showQTail = showQTail,
        modifier = modifier.jogWheelInput(enabled, rotationDeg, onDown = onDown, onMove = onMove, onUp = onUp)
    )
}

/**
 * Touch handling of a jog wheel: a gesture that starts inside the wheel reports the rotation
 * (radians since touch-down, around the wheel's centre) and turns [rotationDeg] for the face.
 *
 * @param wheelBounds the wheel's square within this element; then only touches on its circle are
 *        taken, the others reach the elements below (the classic layout's chips peek out beside
 *        the wheel). Checked in the Initial pass, before those elements see the touch, and
 *        consumed if it is the wheel's. null: the centred square, and any touch in the element.
 */
@Composable
fun Modifier.jogWheelInput(
    enabled: Boolean,
    rotationDeg: MutableState<Float>,
    wheelBounds: ((size: IntSize) -> Rect)? = null,
    onDown: () -> Unit,
    onMove: (textureAngle: Double) -> Unit,
    onUp: () -> Unit
): Modifier {
    val currentOnDown by rememberUpdatedState(onDown)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnUp by rememberUpdatedState(onUp)
    val currentBounds by rememberUpdatedState(wheelBounds)
    // Disabled: no gesture at all. Toggling restarts the input (an ongoing gesture ends).
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val bounds = currentBounds?.invoke(size) ?: run {
                val d = minOf(size.width, size.height).toFloat()
                Rect(Offset((size.width - d) / 2f, (size.height - d) / 2f), Size(d, d))
            }
            val cx = bounds.center.x
            val cy = bounds.center.y
            val radius = bounds.width / 2f
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val dx = down.position.x - cx
            val dy = down.position.y - cy
            // With explicit bounds only touches on the circle count; otherwise the whole square.
            val circleOnly = currentBounds != null
            if (circleOnly && dx * dx + dy * dy > radius * radius) return@awaitEachGesture
            down.consume()
            var lastRad = atan2(dy.toDouble(), dx.toDouble())
            var accumulated = 0.0
            currentOnDown()

            // finally: also when the gesture is cancelled (e.g. the wheel gets disabled
            // under the finger), so a bent pitch / a scratch never sticks.
            try {
                drag(down.id) { change ->
                    val curRad = atan2(
                        (change.position.y - cy).toDouble(),
                        (change.position.x - cx).toDouble()
                    )
                    var delta = curRad - lastRad
                    if (delta > PI) delta -= 2 * PI
                    if (delta < -PI) delta += 2 * PI
                    accumulated += delta
                    lastRad = curRad
                    rotationDeg.value = (accumulated * 180.0 / PI).toFloat()
                    change.consume()
                    currentOnMove(accumulated)
                }
            } finally {
                rotationDeg.value = 0f
                currentOnUp()
            }
        }
    }
}

/**
 * The jog wheel's look, square: the wheel image turned by [rotationDeg] on white, a white rim,
 * and (if [showQTail]) the orange "Q" stick from the centre to the bottom-right corner.
 */
@Composable
fun JogWheelFace(
    rotationDeg: State<Float>,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 8.dp,
    showQTail: Boolean = true
) {
    val painter = painterResource(Res.drawable.qpl_btn_wheel_ohne_rand01)
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f

        // Wheel image + white background, kept circular now that the Canvas itself is no longer
        // clipped to a circle (so the Q tail below can extend into the square's corner).
        val circle = Path().apply {
            addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
        }
        clipPath(circle) {
            drawCircle(Color.White, radius = radius)
            rotate(rotationDeg.value) {
                with(painter) {
                    draw(size)
                }
            }
        }

        // White rim on top of the rimless ("ohne rand") wheel image.
        val stroke = borderWidth.toPx()
        drawCircle(
            color = Color.White,
            radius = radius - stroke / 2f,
            style = Stroke(width = stroke)
        )

        // Static "Q tail": a QOrange line from the center to the bottom-right corner of the
        // square container, with rounded ends. Drawn last (outside rotate + the circle clip) so
        // it never spins with the wheel and isn't cut off at the rim.
        if (showQTail) {
            drawLine(
                color = QOrange,
                start = Offset(cx, cy),
                end = Offset(size.width, size.height),
                strokeWidth = 32.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
