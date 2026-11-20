package org.qstuff.qplayer.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.qstuff.qplayer.ui.player.jogwheel.jogWheelInput
import org.qstuff.qplayer.ui.player.jogwheel.JogWheelFace
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.button_next
import org.qstuff.qplayer.resources.button_previous
import org.qstuff.qplayer.resources.ic_keyboard_arrow_left_white_24px
import org.qstuff.qplayer.resources.ic_keyboard_arrow_right_white_24px
import org.qstuff.qplayer.ui.theme.ChipLabel
import org.qstuff.qplayer.ui.theme.LocalDimens
import org.qstuff.qplayer.ui.theme.QOrange
import org.qstuff.qplayer.ui.theme.rememberChipLabelStyle

/**
 * How far (in button widths) the jog wheel's left edge stays right of the third button column,
 * so the chips there (reset, mt, play) keep a visible, tappable part beside the wheel.
 */
private const val JOG_INSET_UNITS = 0.75f

/**
 * Sizes of the classic deck for a [width] wide area and button rows [rowHeight] tall. All gaps
 * are [ControlChipSpacing], like in the modern layout.
 *
 * Columns: fader (one button wide) · two square button columns · a third column that runs under
 * the jog wheel to the right edge. Rows: pitch value · range · repeat/shuffle (square) · cue ·
 * prev/next/play (square). The jog wheel is as tall as these rows and right-aligned; [unit] (the
 * button width) is chosen so its left edge stays [JOG_INSET_UNITS] right of the third column.
 */
class ClassicDeckMetrics(width: Dp, val rowHeight: Dp) {
    val gap = ControlChipSpacing
    // jog = 3 rows + 2 units + 4 gaps must fit: width − fader slot − 2 columns − inset
    val unit = (width - rowHeight * 3 - gap * 7) / (5 + JOG_INSET_UNITS)
    val jogSize = rowHeight * 3 + unit * 2 + gap * 4
    val faderWidth = unit
}

/**
 * The classic (original qplayer) player deck for phones, below the title bar: the pitch fader on
 * the left; beside it the buttons with the jog wheel on their right — the wheel covers the
 * third column's chips partly, as in the original design — and below them the track title and
 * duration rows ([trackInfo], which brings its own 4dp vertical padding).
 *
 * @param trackInfoRowHeight height of one [trackInfo] row, so the fader ends with the duration row.
 */
@Composable
fun ClassicPlayerDeck(
    controls: PlayerControlsModel,
    pitchProgress: Int,
    jogActions: JogWheelActions,
    jogEnabled: Boolean,
    trackInfoRowHeight: Dp,
    trackInfo: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val m = ClassicDeckMetrics(maxWidth, LocalDimens.current.textviewHeight)
        val labelStyle = rememberChipLabelStyle(
            chipWidth = m.unit,
            chipHeight = m.rowHeight,
            labels = remember(controls.pitchRangeValues) { controlChipLabels(controls.pitchRangeValues) }
        )

        Row(verticalAlignment = Alignment.Top) {
            // Fader: from the first button row down to the end of the duration row
            PitchFader(
                pitchProgress = pitchProgress,
                onPitchChange = jogActions.onPitchChange,
                modifier = Modifier
                    .width(m.faderWidth)
                    .height(m.jogSize + m.gap + trackInfoRowHeight * 2 + m.gap)
            )
            Spacer(Modifier.width(m.gap))
            Column(Modifier.weight(1f)) {
                val handler = rememberJogWheelHandler(jogActions)
                val rotationDeg = remember { mutableStateOf(0f) }
                val radius = LocalDimens.current.roundedShapeRadius
                // The wheel's touch handling sits on the whole block, limited to the wheel's
                // circle (top right): touches beside it reach the chips below it.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(m.jogSize)
                        // Right of the wheel's centre one continuous black area, instead of the
                        // white gaps between reset, mt and play: from the top of the block down
                        // into the track title row (drawn after it, so the row's rounded corner
                        // sits on black). Left of the centre the chips keep their gaps.
                        .drawBehind {
                            val left = size.width - m.jogSize.toPx() / 2f
                            val bottom = size.height + (m.gap + radius).toPx()
                            val r = CornerRadius(radius.toPx())
                            drawPath(
                                Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            left = left, top = 0f, right = size.width, bottom = bottom,
                                            topLeftCornerRadius = CornerRadius.Zero,
                                            topRightCornerRadius = r,
                                            bottomRightCornerRadius = CornerRadius.Zero,
                                            bottomLeftCornerRadius = CornerRadius.Zero
                                        )
                                    )
                                },
                                color = Color.Black
                            )
                        }
                        .jogWheelInput(
                            enabled = jogEnabled,
                            rotationDeg = rotationDeg,
                            wheelBounds = { size ->
                                val d = size.height.toFloat()
                                Rect(Offset(size.width - d, 0f), Size(d, d))
                            },
                            onDown = handler::onDown,
                            onMove = handler::onMove,
                            onUp = handler::onUp
                        )
                ) {
                    ClassicButtons(controls, m, labelStyle)
                    // On top of the third column's chips; outside its circle the canvas is clear.
                    JogWheelFace(
                        rotationDeg = rotationDeg,
                        showQTail = false,
                        modifier = Modifier.align(Alignment.TopEnd).size(m.jogSize)
                    )
                }
                trackInfo()
            }
        }
    }
}

@Composable
private fun ClassicButtons(
    controls: PlayerControlsModel,
    m: ClassicDeckMetrics,
    labelStyle: TextStyle
) {
    val twoColumns = Modifier.width(m.unit * 2 + m.gap).height(m.rowHeight)
    val square = Modifier.size(m.unit)
    Column(verticalArrangement = Arrangement.spacedBy(m.gap)) {
        // pitch value with fine-tuning arrows | reset
        Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            PitchValueChip(controls, labelStyle, twoColumns)
            UnderJogChip(m, onClick = controls.onReset) {
                ChipLabel(text = "reset", color = Color.White, style = labelStyle)
            }
        }
        // pitch range | master tempo
        Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            PitchRangeChip(controls, labelStyle, twoColumns, color = QOrange)
            UnderJogChip(m, onClick = controls.onToggleMasterTempo) {
                ChipLabel(
                    text = "mt",
                    color = if (controls.masterTempo) QOrange else Color.White,
                    style = labelStyle
                )
            }
        }
        // repeat | shuffle
        Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            IconChip(controls.repeatIcon, "Repeat", controls.onToggleRepeat, square)
            IconChip(controls.shuffleIcon, "Shuffle", controls.onToggleShuffle, square)
        }
        // cue
        TextChip(
            text = "cue",
            labelStyle = labelStyle,
            modifier = Modifier.width(m.unit).height(m.rowHeight),
            onClick = controls.onCueClick,
            onLongClick = controls.onCueLongClick,
            color = if (controls.cueActive) QOrange else Color.White
        )
        // previous | next | play
        Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            IconChip(Res.drawable.button_previous, "Previous", controls.onPrevious, square, tint = Color.White)
            IconChip(Res.drawable.button_next, "Next", controls.onNext, square, tint = Color.White)
            UnderJogChip(m, height = m.unit, onClick = controls.onPlayPause) {
                ChipIcon(controls.playPauseIcon, "Play/Pause")
            }
        }
    }
}

/**
 * A third-column chip: runs from its column to the right edge, under the jog wheel. Its content
 * is centred in the first button width — the part beside the wheel.
 */
@Composable
private fun UnderJogChip(
    m: ClassicDeckMetrics,
    height: Dp = m.rowHeight,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) = ChipBox(
    modifier = Modifier.fillMaxWidth().height(height),
    onClick = onClick,
    contentAlignment = Alignment.CenterStart
) {
    Box(Modifier.width(m.unit).fillMaxHeight(), contentAlignment = Alignment.Center, content = content)
}

/** "‹ +12,8 % ›": the pitch value with the fine-tuning arrows at both ends (one chip). */
@Composable
private fun PitchValueChip(
    controls: PlayerControlsModel,
    labelStyle: TextStyle,
    modifier: Modifier
) = ChipBox(modifier = modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.fillMaxHeight().clickable(onClick = controls.onPitchDecrease).width(ARROW_TAP_WIDTH),
            contentAlignment = Alignment.Center
        ) {
            ChipIcon(Res.drawable.ic_keyboard_arrow_left_white_24px, "Pitch decrease", tint = QOrange)
        }
        // The value carries leading spaces as a modern-layout alignment hack: trimmed here.
        Text(
            text = controls.pitchValueText.trim(),
            color = Color.White,
            style = labelStyle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier.fillMaxHeight().clickable(onClick = controls.onPitchIncrease).width(ARROW_TAP_WIDTH),
            contentAlignment = Alignment.Center
        ) {
            ChipIcon(Res.drawable.ic_keyboard_arrow_right_white_24px, "Pitch increase", tint = QOrange)
        }
    }
}

/** Tap area of each fine-tuning arrow in [PitchValueChip]. */
private val ARROW_TAP_WIDTH = 28.dp
