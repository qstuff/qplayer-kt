package org.qstuff.qplayer.ui.player.jogwheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.qstuff.qplayer.R
import org.qstuff.qplayer.ui.theme.QOrange
import kotlin.math.PI
import kotlin.math.atan2

@Composable
fun JogWheel(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 8.dp,
    onDown: () -> Unit,
    onMove: (textureAngle: Double) -> Unit,
    onUp: () -> Unit
) {
    val rotationDeg = remember { mutableStateOf(0f) }
    val currentOnDown by rememberUpdatedState(onDown)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnUp by rememberUpdatedState(onUp)
    val painter = painterResource(R.drawable.qpl_btn_wheel_ohne_rand01)
    
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    var lastRad = atan2(
                        (down.position.y - cy).toDouble(),
                        (down.position.x - cx).toDouble()
                    )
                    var accumulated = 0.0
                    currentOnDown()

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

                    rotationDeg.value = 0f
                    currentOnUp()
                }
            }
    ) {
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
        drawLine(
            color = QOrange,
            start = Offset(cx, cy),
            end = Offset(size.width, size.height),
            strokeWidth = 32.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
