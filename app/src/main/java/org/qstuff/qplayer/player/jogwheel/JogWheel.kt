package org.qstuff.qplayer.player.jogwheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.qstuff.qplayer.R
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
            .clip(CircleShape)
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
        drawCircle(Color.White, radius = size.minDimension / 2f)
        rotate(rotationDeg.value) {
            with(painter) {
                draw(size)
            }
        }
        // White rim on top of the rimless ("ohne rand") wheel image.
        val stroke = borderWidth.toPx()
        drawCircle(
            color = Color.White,
            radius = size.minDimension / 2f - stroke / 2f,
            style = Stroke(width = stroke)
        )
    }
}
