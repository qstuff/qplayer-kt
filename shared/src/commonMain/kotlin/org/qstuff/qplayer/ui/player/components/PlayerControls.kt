package org.qstuff.qplayer.ui.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.unit.dp
import org.qstuff.qplayer.ui.theme.ChipLabel
import org.qstuff.qplayer.ui.theme.LocalDimens
import org.qstuff.qplayer.ui.theme.QOrange
import org.qstuff.qplayer.ui.theme.chipIconSizeMatching
import org.qstuff.qplayer.ui.theme.rememberChipLabelStyle
import org.qstuff.qplayer.util.TrackRepeatStatus
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.button_loop
import org.qstuff.qplayer.resources.button_loop1_selected
import org.qstuff.qplayer.resources.button_loop_selected
import org.qstuff.qplayer.resources.button_next
import org.qstuff.qplayer.resources.button_pause_selected
import org.qstuff.qplayer.resources.button_play_selected
import org.qstuff.qplayer.resources.button_previous
import org.qstuff.qplayer.resources.button_shuffle
import org.qstuff.qplayer.resources.button_shuffle_selected
import org.qstuff.qplayer.resources.ic_keyboard_arrow_left_white_24px
import org.qstuff.qplayer.resources.ic_keyboard_arrow_right_white_24px

/** Gap between the control chips (horizontal and between the rows). */
val ControlChipSpacing = 4.dp

/** Widest pitch value texts (see PlayerViewModel's pitch formatting) for the shared label size. */
private val PITCH_VALUE_SAMPLES = listOf("+88,8%", "-88,8%", "-100,0", "+100,0")

/** Every label a text chip can show — for one shared label size (rememberChipLabelStyle). */
fun controlChipLabels(pitchRangeValues: List<String>): List<String> =
    PITCH_VALUE_SAMPLES + pitchRangeValues + listOf("reset", "mt", "cue")

/**
 * State and actions of the player buttons, shared by both player layouts ([PlayerControls] and
 * the classic deck).
 */
class PlayerControlsModel(
    val isPlaying: Boolean,
    val repeat: TrackRepeatStatus?,
    val shuffle: Boolean,
    val cueActive: Boolean,
    val masterTempo: Boolean,
    val pitchValueText: String,
    val pitchRangeValues: List<String>,
    val pitchFactorIndex: Int,
    val onPrevious: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onToggleRepeat: () -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCueClick: () -> Unit,
    val onCueLongClick: () -> Unit,
    val onPitchDecrease: () -> Unit,
    val onPitchIncrease: () -> Unit,
    val onPitchRangeSelected: (Int) -> Unit,
    val onReset: () -> Unit,
    val onToggleMasterTempo: () -> Unit
) {
    /** The drawable is already QOrange. */
    val playPauseIcon: DrawableResource
        get() = if (isPlaying) Res.drawable.button_pause_selected else Res.drawable.button_play_selected

    /** Repeat icons encode the state by their own color (white = off, orange = on) and the "1" — no tint. */
    val repeatIcon: DrawableResource
        get() = when (repeat) {
            TrackRepeatStatus.ONE -> Res.drawable.button_loop1_selected
            TrackRepeatStatus.ALL -> Res.drawable.button_loop_selected
            else -> Res.drawable.button_loop
        }

    /** Shuffle drawables also carry their own on/off colors — no tint. */
    val shuffleIcon: DrawableResource
        get() = if (shuffle) Res.drawable.button_shuffle_selected else Res.drawable.button_shuffle

    val pitchRangeText: String get() = pitchRangeValues.getOrElse(pitchFactorIndex) { "?" }
}

/**
 * The pitch range chip: shows the current range and opens a menu with all ranges. Its size comes
 * from [modifier].
 */
@Composable
fun PitchRangeChip(
    model: PlayerControlsModel,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    var showPitchRangeMenu by remember { mutableStateOf(false) }
    ChipBox(modifier = modifier, onClick = { showPitchRangeMenu = true }) {
        ChipLabel(text = model.pitchRangeText, color = color, style = labelStyle)
        DropdownMenu(
            expanded = showPitchRangeMenu,
            onDismissRequest = { showPitchRangeMenu = false }
        ) {
            model.pitchRangeValues.forEachIndexed { index, value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        model.onPitchRangeSelected(index)
                        showPitchRangeMenu = false
                    }
                )
            }
        }
    }
}

/** Chips per button row — all chips share the row width equally. */
private const val CONTROL_CHIPS_PER_ROW = 6

/** Width of one control chip in a button row [rowWidth] wide (mirrors the weight(1f) layout). */
fun controlChipWidth(rowWidth: Dp): Dp =
    (rowWidth - ControlChipSpacing * (CONTROL_CHIPS_PER_ROW - 1)) / CONTROL_CHIPS_PER_ROW

/**
 * The two transport/pitch control button rows, built from [ControlChip]s.
 *
 *  Row 1: prev · play/pause · next · repeat · shuffle · cue
 *  Row 2: pitch− · value · pitch+ · range · reset · mt
 */
@Composable
fun PlayerControls(
    model: PlayerControlsModel,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        // One label size for all text chips: the widest possible label must fit one chip. The
        // pitch value samples cover its widest formats, so the size doesn't jump while pitching.
        val labelStyle = rememberChipLabelStyle(
            chipWidth = controlChipWidth(maxWidth),
            chipHeight = LocalDimens.current.textviewHeight,
            labels = remember(model.pitchRangeValues) { controlChipLabels(model.pitchRangeValues) }
        )
        // The pitch arrows' glyph is 12 of the drawable's 24 units tall: match the label caps.
        val arrowIconSize = chipIconSizeMatching(
            labelStyle = labelStyle,
            glyphFraction = 12f / 24f,
            chipHeight = LocalDimens.current.textviewHeight
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            // ─── Row 1: prev | play | next | repeat | shuffle | cue ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(ControlChipSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconControlChip(
                    Res.drawable.button_previous, "Previous", model.onPrevious,
                    tint = QOrange   // white drawable → tint to the accent
                )
                IconControlChip(model.playPauseIcon, "Play/Pause", model.onPlayPause)
                IconControlChip(
                    Res.drawable.button_next, "Next", model.onNext,
                    tint = QOrange   // white drawable → tint to the accent
                )
                IconControlChip(model.repeatIcon, "Repeat", model.onToggleRepeat)
                IconControlChip(model.shuffleIcon, "Shuffle", model.onToggleShuffle)
                TextControlChip(
                    text = "cue",
                    labelStyle = labelStyle,
                    color = if (model.cueActive) QOrange else Color.White,
                    onClick = model.onCueClick,
                    onLongClick = model.onCueLongClick
                )
            }

            // ─── Row 2: pitch- | value | pitch+ | range | reset | mt ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(ControlChipSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconControlChip(
                    Res.drawable.ic_keyboard_arrow_left_white_24px, "Pitch decrease", model.onPitchDecrease,
                    tint = QOrange,   // white drawable → tint to the accent
                    iconSize = arrowIconSize
                )
                TextControlChip(text = model.pitchValueText, labelStyle = labelStyle)
                IconControlChip(
                    Res.drawable.ic_keyboard_arrow_right_white_24px, "Pitch increase", model.onPitchIncrease,
                    tint = QOrange,   // white drawable → tint to the accent
                    iconSize = arrowIconSize
                )
                PitchRangeChip(
                    model = model,
                    labelStyle = labelStyle,
                    modifier = Modifier.weight(1f).height(LocalDimens.current.textviewHeight)
                )
                TextControlChip(text = "reset", labelStyle = labelStyle, onClick = model.onReset)
                TextControlChip(
                    text = "mt",
                    labelStyle = labelStyle,
                    color = if (model.masterTempo) QOrange else Color.White,
                    onClick = model.onToggleMasterTempo
                )
            }
        }
    }
}
