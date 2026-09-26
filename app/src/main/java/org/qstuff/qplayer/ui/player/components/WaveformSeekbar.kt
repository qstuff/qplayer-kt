package org.qstuff.qplayer.ui.player.components

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.TrackData
import org.qstuff.qplayer.ui.player.trackprogress.CuepointView
import org.qstuff.qplayer.ui.player.trackprogress.WaveformView
import org.qstuff.qplayer.ui.theme.QOrange

/**
 * The seekbar area: the overview waveform (an [WaveformView]), a "calculating…" hint while the
 * overview is still being generated, a translucent-orange progress fill + playhead line that
 * can be dragged/tapped to seek, and the cue-point marker overlay.
 *
 * State stays in the parent: [onSeekChange] fires live while dragging (parent updates its
 * seek preview), and [onSeekCommit] fires once on release with the final fraction.
 */
@Composable
fun WaveformSeekbar(
    waveformData: TrackData?,
    waveformReady: Boolean,
    showCalculating: Boolean,
    displayedProgress: Float,
    dynamicTimeAlpha: Float,
    cueActive: Boolean,
    cueProgressPos: Int,
    onSeekChange: (fraction: Float) -> Unit,
    onSeekCommit: (fraction: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val roundedShape = RoundedCornerShape(dimensionResource(R.dimen.rounded_shape_radius))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.seekbar_height))
    ) {
        AndroidView(
            factory = { ctx ->
                WaveformView(ctx).apply { updateWaveform(null) }
            },
            update = { view ->
                // Show the current track's overview, or clear it (on track change or while
                // it's still being generated) so a previous track's waveform never lingers.
                if (waveformReady) view.updateWaveform(waveformData)
                else view.updateWaveform(null)
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(roundedShape)
                .background(Color.Black, roundedShape)
        )
        if (showCalculating) {
            Text(
                text = "calculating waveform data…",
                color = QOrange.copy(alpha = 0.67f),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        // Progress: translucent orange fill over the played portion + a full-height QOrange
        // playhead line. Drag/tap to seek — the parent short-circuits its timer while dragging.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(roundedShape)
                .graphicsLayer { alpha = dynamicTimeAlpha }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var fraction = (down.position.x / size.width).coerceIn(0f, 1f)
                        onSeekChange(fraction)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onSeekChange(fraction)
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        onSeekCommit(fraction)
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
                view.visibility = if (cueActive) View.VISIBLE else View.INVISIBLE
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
