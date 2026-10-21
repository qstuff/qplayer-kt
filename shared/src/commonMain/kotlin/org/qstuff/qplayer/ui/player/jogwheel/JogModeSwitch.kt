package org.qstuff.qplayer.ui.player.jogwheel

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.automirrored.filled.RotateRight
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
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.jogwheel_mode_entries
import org.qstuff.qplayer.ui.theme.QOrange
import org.qstuff.qplayer.util.JogwheelMode

private val ButtonBackground = Color(0xFF1A1A1A)

private fun JogwheelMode.icon(): ImageVector = when (this) {
    JogwheelMode.SPEED_ANGULAR -> Icons.AutoMirrored.Filled.RotateRight
    JogwheelMode.SPEED_VELOCITY -> Icons.Filled.Speed
    JogwheelMode.SCRATCH -> Icons.Filled.Album
}

/**
 * Quick switch for the jog wheel mode, for a corner of the jog wheel's square: a round button
 * showing the current [mode]; a tap unfolds the three modes below it (speed-dial style, above
 * everything else). Choosing one calls [onModeChange] — the same setting as in Settings — and
 * folds the options again; a tap anywhere else just folds them.
 */
@Composable
fun JogModeSwitch(
    mode: JogwheelMode,
    onModeChange: (JogwheelMode) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 36.dp
) {
    var expanded by remember { mutableStateOf(false) }
    // Entries are "Name:\nDescription" — the options only show the name (as the Settings summary).
    val names = stringArrayResource(Res.array.jogwheel_mode_entries)
        .map { it.trim().substringBefore('\n').removeSuffix(":") }

    Box(modifier) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .background(ButtonBackground, CircleShape)
                .border(1.5.dp, if (expanded) QOrange else Color.White, CircleShape)
                .clickable { expanded = !expanded },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = mode.icon(),
                contentDescription = names.getOrNull(mode.ordinal),
                tint = QOrange,
                modifier = Modifier.size(buttonSize * 0.6f)
            )
        }

        if (expanded) {
            val gap = with(LocalDensity.current) { (buttonSize + 6.dp).roundToPx() }
            Popup(
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        JogwheelMode.entries.forEach { option ->
                            ModeOption(
                                icon = option.icon(),
                                label = names.getOrElse(option.ordinal) { option.name },
                                selected = option == mode,
                                height = buttonSize,
                                onClick = {
                                    expanded = false
                                    if (option != mode) onModeChange(option)
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
private fun ModeOption(
    icon: ImageVector,
    label: String,
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
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(height * 0.6f))
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
