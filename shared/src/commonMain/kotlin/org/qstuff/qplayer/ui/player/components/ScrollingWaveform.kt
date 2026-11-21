package org.qstuff.qplayer.ui.player.components

import androidx.compose.foundation.Canvas
import org.qstuff.qplayer.util.Formatting
import org.qstuff.qplayer.ui.player.BeatGrid
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import org.qstuff.qplayer.ui.theme.LocalDimens
import org.qstuff.qplayer.ui.theme.QOrange

/** Shown track time when no tempo is known: about 4 bars of typical dance music. */
const val FALLBACK_WINDOW_SECONDS = 8f

/** Width of one pre-rendered strip of the scrolling waveform. */
private const val TILE_PX = 512

/** Strips kept in memory: the visible ones (2–3) plus a few around them. */
private const val MAX_TILES = 8

/** Beats shown at once when the tempo is known: 4 bars of 4/4. */
private const val BEATS_SHOWN = 16

/**
 * Scrolling detail waveform: 4 bars ([BEATS_SHOWN] beats of [beatGrid]) of the track around the
 * playback position — or [FALLBACK_WINDOW_SECONDS] without a steady beat — with the playhead
 * fixed in the middle, scrolling right to left while playing (and following the jog wheel while
 * scratching). With a beat grid: orange bar lines, grey beat lines and the current tempo (the
 * track's BPM × [playbackSpeed]) top left. Display only.
 *
 * Built for slow devices: the bars are rendered once into strips of [TILE_PX] px; per frame only
 * the visible strips are drawn, shifted by the position — no per-bar drawing, no recomposition
 * (the position is read in the draw phase), and in its own graphics layer, so the rest of the
 * screen isn't redrawn with it. The window is in track time, so pitch only changes the scrolling
 * speed, not the scale.
 *
 * @param detail one peak 0..127 per [peaksPerSecond]-th of a second of the track (may cover only
 *        the decoded part while the waveform is generated); null: an empty waveform.
 * @param getPositionMs the current playback position, read once per frame.
 */
@Composable
fun ScrollingWaveform(
    detail: ByteArray?,
    peaksPerSecond: Float,
    beatGrid: BeatGrid?,
    playbackSpeed: Float,
    getPositionMs: () -> Long,
    modifier: Modifier = Modifier
) {
    val currentGetPosition by rememberUpdatedState(getPositionMs)
    val positionMs = remember { mutableLongStateOf(0L) }
    // Once per frame; writing an unchanged value (paused) doesn't redraw.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { positionMs.longValue = currentGetPosition() }
        }
    }
    val windowSeconds = beatGrid?.let { it.beatSeconds * BEATS_SHOWN } ?: FALLBACK_WINDOW_SECONDS
    val tiles = remember(detail, peaksPerSecond, windowSeconds) {
        if (detail == null || detail.isEmpty() || peaksPerSecond <= 0f) null
        else WaveformTiles(detail, peaksPerSecond, windowSeconds)
    }
    val shape = RoundedCornerShape(LocalDimens.current.roundedShapeRadius)

    Box(modifier = modifier) {
        // Own graphics layer: redrawing every frame then only re-records this view, not the whole
        // screen (jog wheel, lists, …).
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer().clip(shape).background(Color.Black)) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            drawLine(QOrange, Offset(0f, centerY), Offset(size.width, centerY), strokeWidth = 1f)
            tiles?.drawAt(this, positionMs.longValue)
            beatGrid?.let { drawBeatLines(it, positionMs.longValue, windowSeconds) }
            // Played part (left of the playhead) and the playhead, like the overview waveform
            drawRect(QOrange.copy(alpha = 0.35f), size = Size(centerX, size.height))
            drawLine(QOrange, Offset(centerX, 0f), Offset(centerX, size.height), strokeWidth = 2.dp.toPx())
        }
        if (beatGrid != null) {
            Text(
                text = "${Formatting.oneDecimal(beatGrid.bpm * playbackSpeed)} BPM",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

/** Grey beat lines and orange bar lines (every 4th beat from the downbeat) in the window. */
private fun DrawScope.drawBeatLines(grid: BeatGrid, positionMs: Long, windowSeconds: Float) {
    val start = positionMs / 1000f - windowSeconds / 2f
    val pxPerSecond = size.width / windowSeconds
    var k = ceil((start - grid.firstBeatSeconds) / grid.beatSeconds).toInt().coerceAtLeast(0)
    while (true) {
        val x = (grid.firstBeatSeconds + k * grid.beatSeconds - start) * pxPerSecond
        if (x > size.width) break
        val isBar = ((k - grid.downbeatIndex) % 4 + 4) % 4 == 0
        drawLine(
            color = if (isBar) QOrange.copy(alpha = 0.8f) else BEAT_LINE,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = (if (isBar) 2.dp else 1.dp).toPx()
        )
        k++
    }
}

/** Grey: visible on the white bars and on black. */
private val BEAT_LINE = Color(0xFF8C8C8C)

/**
 * The detail waveform cut into pre-rendered strips: white bars (symmetric around the centre) on
 * transparent, [TILE_PX] wide, at the scale where [windowSeconds] fill the view's width. Strips
 * are rendered on first use and kept (least recently used ones dropped beyond [MAX_TILES]); a new
 * view size starts over.
 */
private class WaveformTiles(
    private val detail: ByteArray,
    private val peaksPerSecond: Float,
    private val windowSeconds: Float
) {
    private var tileSize = IntSize.Zero
    private val cache = LinkedHashMap<Int, ImageBitmap>()

    fun drawAt(scope: DrawScope, positionMs: Long) = with(scope) {
        val width = size.width
        val height = size.height.roundToInt()
        if (width <= 0f || height <= 0) return
        if (tileSize.height != height || tileSize.width != width.roundToInt()) {
            cache.clear()
            tileSize = IntSize(width.roundToInt(), height)
        }
        val pxPerPeak = width / (windowSeconds * peaksPerSecond)
        // The view's left edge in strip coordinates: the position sits in the middle.
        val leftPx = positionMs / 1000f * peaksPerSecond * pxPerPeak - width / 2f
        val first = floor(leftPx / TILE_PX).toInt().coerceAtLeast(0)
        val last = floor((leftPx + width) / TILE_PX).toInt()
        val lastWithData = ((detail.size * pxPerPeak) / TILE_PX).toInt()
        for (index in first..minOf(last, lastWithData)) {
            val tile = cache.remove(index) ?: render(index, pxPerPeak, height, this)
            cache[index] = tile   // (re)insert: most recently used last
            drawImage(tile, topLeft = Offset(index * TILE_PX - leftPx, 0f))
        }
        while (cache.size > MAX_TILES) cache.remove(cache.keys.first())
    }

    private fun render(index: Int, pxPerPeak: Float, height: Int, density: Density): ImageBitmap {
        val image = ImageBitmap(TILE_PX, height)
        val tileStart = index * TILE_PX
        val firstPeak = floor(tileStart / pxPerPeak).toInt().coerceAtLeast(0)
        val lastPeak = ceil((tileStart + TILE_PX) / pxPerPeak).toInt().coerceAtMost(detail.size - 1)
        val centerY = height / 2f
        val halfMax = (height - 2 * density.run { 4.dp.toPx() }) / 2f
        val barWidth = pxPerPeak.coerceAtLeast(1f)
        val bars = Path()
        for (i in firstPeak..lastPeak) {
            val half = detail[i].toInt().coerceIn(0, 127) / 127f * halfMax
            val left = i * pxPerPeak - tileStart
            bars.addRect(Rect(left, centerY - half, left + barWidth, centerY + half))
        }
        CanvasDrawScope().draw(
            Density(density.density, density.fontScale),
            LayoutDirection.Ltr,
            Canvas(image),
            Size(TILE_PX.toFloat(), height.toFloat())
        ) {
            drawPath(bars, Color.White)
        }
        return image
    }
}
