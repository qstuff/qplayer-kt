package org.qstuff.qplayer.ui.player.jogwheel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.qstuff.qplayer.resources.jogwheel_quick_mode_label
import org.qstuff.qplayer.resources.jogwheel_quick_sensitivity_label
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.jogwheel_mode_short
import org.qstuff.qplayer.resources.jogwheel_sensitivity_short
import org.qstuff.qplayer.resources.jogwheel_sensitivity_values
import org.qstuff.qplayer.ui.theme.QOrange
import org.qstuff.qplayer.util.JogwheelMode

// Quick switches for the jog wheel settings, for the top corners of the jog wheel's square
// (outside the wheel): mode (top left), sensitivity (top right). Same settings as in Settings.

private val ButtonBackground = Color(0xFF1A1A1A)
private val QuickSwitchHeight = 32.dp

/** The jog wheel mode: by angle, by velocity, scratch. */
@Composable
fun JogModeSwitch(
    mode: JogwheelMode,
    onModeChange: (JogwheelMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val names = stringArrayResource(Res.array.jogwheel_mode_short)
    JogQuickSwitch(
        label = stringResource(Res.string.jogwheel_quick_mode_label),
        options = JogwheelMode.entries.map { QuickOption(it, names.getOrElse(it.ordinal) { _ -> it.name }) },
        selected = mode,
        onSelect = onModeChange,
        alignEnd = false,
        modifier = modifier
    )
}

/** The jog wheel sensitivity: slow, mid, fast (the values of the Settings entries). */
@Composable
fun JogSensitivitySwitch(
    sensitivity: Int,
    onSensitivityChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val names = stringArrayResource(Res.array.jogwheel_sensitivity_short)
    val values = stringArrayResource(Res.array.jogwheel_sensitivity_values).map { it.toInt() }
    JogQuickSwitch(
        label = stringResource(Res.string.jogwheel_quick_sensitivity_label),
        options = values.mapIndexed { i, value -> QuickOption(value, names.getOrElse(i) { value.toString() }) },
        selected = sensitivity,
        onSelect = onSensitivityChange,
        alignEnd = true,
        modifier = modifier
    )
}

private class QuickOption<T>(val value: T, val label: String)

/**
 * A pill showing the [selected] option's name, with a small [label] beside it (on the inner side,
 * at the top edge where the corner is widest); a tap unfolds all [options] below it (in a popup
 * above everything else — left- or right-aligned with the pill, [alignEnd] for the right
 * corner). Choosing one calls [onSelect] and folds the options again; a tap anywhere else just
 * folds them.
 */
@Composable
private fun <T> JogQuickSwitch(
    label: String,
    options: List<QuickOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = QuickSwitchHeight
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }

    Box(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            if (alignEnd) QuickSwitchLabel(label, Modifier.padding(end = 4.dp))
            QuickOptionChip(
                label = current?.label ?: "",
                color = QOrange,
                height = height,
                onClick = { expanded = !expanded }
            )
            if (!alignEnd) QuickSwitchLabel(label, Modifier.padding(start = 4.dp))
        }

        if (expanded) {
            val gap = with(LocalDensity.current) { (height + 6.dp).roundToPx() }
            Popup(
                alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
                offset = IntOffset(0, gap),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                // Unfold: fade + grow downwards.
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top)
                ) {
                    Column(
                        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        options.forEach { option ->
                            QuickOptionChip(
                                label = option.label,
                                color = if (option.value == selected) QOrange else Color.White,
                                height = height,
                                onClick = {
                                    expanded = false
                                    if (option.value != selected) onSelect(option.value)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSwitchLabel(text: String, modifier: Modifier) {
    Text(
        text = text,
        color = QOrange,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
    )
}

/** A dark pill with [label] in [color] and a border in the same color. */
@Composable
private fun QuickOptionChip(
    label: String,
    color: Color,
    height: Dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = Modifier
            .height(height)
            .background(ButtonBackground, shape)
            .border(1.5.dp, color, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
