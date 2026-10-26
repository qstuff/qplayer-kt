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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularAlt1Bar
import androidx.compose.material.icons.filled.SignalCellularAlt2Bar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.qstuff.qplayer.resources.jogwheel_mode_entries
import org.qstuff.qplayer.resources.jogwheel_sensitivity_entries
import org.qstuff.qplayer.resources.jogwheel_sensitivity_values
import org.qstuff.qplayer.ui.theme.QOrange
import org.qstuff.qplayer.util.JogwheelMode

// Quick switches for the jog wheel settings, for the top corners of the jog wheel's square
// (outside the wheel): mode (top left), sensitivity (top right). Same settings as in Settings.

private val ButtonBackground = Color(0xFF1A1A1A)
private val QuickSwitchSize = 36.dp

private fun JogwheelMode.icon(): ImageVector = when (this) {
    JogwheelMode.SPEED_ANGULAR -> Icons.AutoMirrored.Filled.RotateRight
    JogwheelMode.SPEED_VELOCITY -> Icons.Filled.Speed
    JogwheelMode.SCRATCH -> Icons.Filled.Album
}

/** Low / medium / high: one, two, three bars. */
private val SensitivityIcons = listOf(
    Icons.Filled.SignalCellularAlt1Bar,
    Icons.Filled.SignalCellularAlt2Bar,
    Icons.Filled.SignalCellularAlt
)

/** The jog wheel mode: by angle, by velocity, scratch. */
@Composable
fun JogModeSwitch(
    mode: JogwheelMode,
    onModeChange: (JogwheelMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Entries are "Name:\nDescription" — the options only show the name (as the Settings summary).
    val names = stringArrayResource(Res.array.jogwheel_mode_entries)
        .map { it.trim().substringBefore('\n').removeSuffix(":") }
    JogQuickSwitch(
        label = stringResource(Res.string.jogwheel_quick_mode_label),
        options = JogwheelMode.entries.map { QuickOption(it, it.icon(), names.getOrElse(it.ordinal) { _ -> it.name }) },
        selected = mode,
        onSelect = onModeChange,
        alignEnd = false,
        modifier = modifier
    )
}

/** The jog wheel sensitivity: low, medium, high (the values of the Settings entries). */
@Composable
fun JogSensitivitySwitch(
    sensitivity: Int,
    onSensitivityChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val names = stringArrayResource(Res.array.jogwheel_sensitivity_entries)
    val values = stringArrayResource(Res.array.jogwheel_sensitivity_values).map { it.toInt() }
    JogQuickSwitch(
        label = stringResource(Res.string.jogwheel_quick_sensitivity_label),
        options = values.mapIndexed { i, value ->
            QuickOption(value, SensitivityIcons.getOrElse(i) { SensitivityIcons.last() }, names.getOrElse(i) { value.toString() })
        },
        selected = sensitivity,
        onSelect = onSensitivityChange,
        alignEnd = true,
        modifier = modifier
    )
}

private class QuickOption<T>(val value: T, val icon: ImageVector, val label: String)

/**
 * A round button showing the [selected] option's icon, with a small [label] beside it (on the
 * inner side, at the top edge where the corner is widest); a tap unfolds all [options] below it
 * (speed-dial style, in a popup above everything else — left- or right-aligned with the button,
 * [alignEnd] for the right corner). Choosing one calls [onSelect] and folds the options again;
 * a tap anywhere else just folds them.
 */
@Composable
private fun <T> JogQuickSwitch(
    label: String,
    options: List<QuickOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
    buttonSize: Dp = QuickSwitchSize
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }

    Box(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            if (alignEnd) QuickSwitchLabel(label, Modifier.padding(end = 4.dp))
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .background(ButtonBackground, CircleShape)
                    .border(1.5.dp, QOrange, CircleShape)
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) {
                current?.let {
                    Icon(
                        imageVector = it.icon,
                        contentDescription = it.label,
                        tint = QOrange,
                        modifier = Modifier.size(buttonSize * 0.6f)
                    )
                }
            }
            if (!alignEnd) QuickSwitchLabel(label, Modifier.padding(start = 4.dp))
        }

        if (expanded) {
            val gap = with(LocalDensity.current) { (buttonSize + 6.dp).roundToPx() }
            Popup(
                alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
                offset = IntOffset(0, gap),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                // Unfold like a speed dial: fade + grow downwards.
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
                                option = option,
                                selected = option.value == selected,
                                height = buttonSize,
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

@Composable
private fun QuickOptionChip(
    option: QuickOption<*>,
    selected: Boolean,
    height: Dp,
    onClick: () -> Unit
) {
    val color = if (selected) QOrange else Color.White
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier = Modifier
            .height(height)
            .background(ButtonBackground, shape)
            .border(1.5.dp, color, shape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = option.icon, contentDescription = null, tint = color, modifier = Modifier.size(height * 0.6f))
        Spacer(Modifier.width(8.dp))
        Text(text = option.label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
