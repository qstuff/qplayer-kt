package org.qstuff.qplayer.ui.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import org.qstuff.qplayer.R
import org.qstuff.qplayer.ui.theme.QOrange
import org.qstuff.qplayer.util.TrackRepeatStatus

/**
 * The two transport/pitch control button rows, built from [ControlChip]s.
 *
 *  Row 1: prev · play/pause · next · repeat · shuffle · cue
 *  Row 2: pitch− · value · pitch+ · range · reset · mt
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    repeat: TrackRepeatStatus?,
    shuffle: Boolean,
    cueActive: Boolean,
    masterTempo: Boolean,
    pitchValueText: String,
    pitchRangeValues: Array<String>,
    pitchFactorIndex: Int,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCueClick: () -> Unit,
    onCueLongClick: () -> Unit,
    onPitchDecrease: () -> Unit,
    onPitchIncrease: () -> Unit,
    onPitchRangeSelected: (Int) -> Unit,
    onReset: () -> Unit,
    onToggleMasterTempo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // ─── Row 1: prev | play | next | repeat | shuffle | cue ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconControlChip(R.drawable.button_previous, "Previous", onPrevious)
            IconControlChip(
                iconRes = if (isPlaying) R.drawable.button_pause_selected
                else R.drawable.button_play_selected,
                contentDescription = "Play/Pause",
                onClick = onPlayPause
            )
            IconControlChip(R.drawable.button_next, "Next", onNext)
            IconControlChip(
                iconRes = when (repeat) {
                    TrackRepeatStatus.ONE -> R.drawable.button_loop1_selected
                    TrackRepeatStatus.ALL -> R.drawable.button_loop_selected
                    else -> R.drawable.button_loop
                },
                contentDescription = "Repeat",
                onClick = onToggleRepeat
            )
            IconControlChip(
                iconRes = if (shuffle) R.drawable.button_shuffle_selected
                else R.drawable.button_shuffle,
                contentDescription = "Shuffle",
                onClick = onToggleShuffle
            )
            TextControlChip(
                text = "cue",
                color = if (cueActive) QOrange else Color.White,
                onClick = onCueClick,
                onLongClick = onCueLongClick
            )
        }

        // ─── Row 2: pitch- | value | pitch+ | range | reset | mt ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconControlChip(
                R.drawable.ic_keyboard_arrow_left_white_24px, "Pitch decrease", onPitchDecrease
            )
            TextControlChip(text = pitchValueText)
            IconControlChip(
                R.drawable.ic_keyboard_arrow_right_white_24px, "Pitch increase", onPitchIncrease
            )
            // Pitch range chip hosts its own dropdown menu.
            var showPitchRangeMenu by remember { mutableStateOf(false) }
            ControlChip(onClick = { showPitchRangeMenu = true }) {
                Text(
                    text = pitchRangeValues.getOrElse(pitchFactorIndex) { "?" },
                    color = Color.White
                )
                DropdownMenu(
                    expanded = showPitchRangeMenu,
                    onDismissRequest = { showPitchRangeMenu = false }
                ) {
                    pitchRangeValues.forEachIndexed { index, value ->
                        DropdownMenuItem(
                            text = { Text(value) },
                            onClick = {
                                onPitchRangeSelected(index)
                                showPitchRangeMenu = false
                            }
                        )
                    }
                }
            }
            TextControlChip(text = "reset", onClick = onReset)
            TextControlChip(
                text = "mt",
                color = if (masterTempo) QOrange else Color.White,
                onClick = onToggleMasterTempo
            )
        }
    }
}
