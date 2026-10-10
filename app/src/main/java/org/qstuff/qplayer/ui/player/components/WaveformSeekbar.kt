package org.qstuff.qplayer.ui.player.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.TrackData
import org.qstuff.qplayer.ui.theme.QOrange

/**
 * The seekbar area: the overview waveform, a "calculating…" hint while the overview is still
 * being generated, a translucent-orange progress fill + playhead line that can be dragged/tapped
 * to seek, and the cue-point marker overlay. All plain Compose drawing; the waveform, the
 * progress and the cue marker use the same full width, so a cue sits exactly where the playhead
 * was when it was set.
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
    // The seek gesture runs in pointerInput(Unit), which is launched once and captures these
    // lambdas from the first composition — so route through rememberUpdatedState to always call
    // the latest ones (they close over the current track/duration).
    val currentOnSeekChange by rememberUpdatedState(onSeekChange)
    val currentOnSeekCommit by rememberUpdatedState(onSeekCommit)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.seekbar_height))
    ) {
        // Show the current track's overview, or nothing (on track change or while it's still
        // being generated) so a previous track's waveform never lingers.
        Waveform(
            peaks = if (waveformReady) waveformData?.bytes else null,
            verticalInset = dimensionResource(R.dimen.waveform_top_margin),
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
                        currentOnSeekChange(fraction)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            currentOnSeekChange(fraction)
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        currentOnSeekCommit(fraction)
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
        if (cueActive) {
            CueMarker(
                position = cueProgressPos / 1000f,
                markerWidth = dimensionResource(R.dimen.cue_marker_width),
                markerOverhang = dimensionResource(R.dimen.cue_marker_top_margin),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Overview waveform: an orange center line and one white bar per peak (0..127), symmetric around
 * the center and spread over the full width. The bars are built once per [peaks] array (and
 * size) — not on every progress update.
 */
@Composable
private fun Waveform(peaks: ByteArray?, verticalInset: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val centerY = size.height / 2f
            val bars = Path()
            if (peaks != null && peaks.isNotEmpty()) {
                val barWidth = size.width / peaks.size
                val drawnWidth = barWidth.coerceAtLeast(1f)
                val halfMax = (size.height - 2 * verticalInset.toPx()) / 2f
                peaks.forEachIndexed { i, peak ->
                    val half = peak.toInt().coerceIn(0, 127) / 127f * halfMax
                    val left = i * barWidth + (barWidth - drawnWidth) / 2f
                    bars.addRect(
                        Rect(left, centerY - half, left + drawnWidth, centerY + half)
                    )
                }
            }
            onDrawBehind {
                drawLine(QOrange, Offset(0f, centerY), Offset(size.width, centerY), strokeWidth = 1f)
                drawPath(bars, Color.White)
            }
        }
    )
}

/**
 * Cue-point marker at [position] (0..1 of the width): an orange funnel from the top edge
 * ([markerWidth] wide, starting [markerOverhang] above the top) narrowing to a 2px line at a fifth
 * of the height, which runs down to the bottom. Clipped to the seekbar, like the former View.
 */
@Composable
private fun CueMarker(position: Float, markerWidth: Dp, markerOverhang: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clipToBounds()) {
        val x = position.coerceIn(0f, 1f) * size.width
        val halfWidth = markerWidth.toPx() / 2f
        val overhang = markerOverhang.toPx()
        val neckY = size.height / 5f
        val line = 1.dp.toPx()
        val marker = Path().apply {
            moveTo(x - halfWidth, -overhang)
            lineTo(x + halfWidth, -overhang)
            lineTo(x + line, neckY)
            lineTo(x + line, size.height + overhang)
            lineTo(x - line, size.height + overhang)
            lineTo(x - line, neckY)
            close()
        }
        drawPath(marker, QOrange)
    }
}
