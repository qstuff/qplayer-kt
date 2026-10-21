package org.qstuff.qplayer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.dialog_cancel
import org.qstuff.qplayer.resources.jogwheel_haptics_entries
import org.qstuff.qplayer.resources.more_menu_settings
import org.qstuff.qplayer.resources.navigate_back
import org.qstuff.qplayer.resources.settings_app_version_title
import org.qstuff.qplayer.resources.settings_cat_dj_specific_options_title
import org.qstuff.qplayer.resources.settings_cat_player_basic_title
import org.qstuff.qplayer.resources.settings_cat_privacy_title
import org.qstuff.qplayer.resources.settings_device_info_title
import org.qstuff.qplayer.resources.settings_dj_jogwheel_haptics_summary
import org.qstuff.qplayer.resources.settings_dj_jogwheel_haptics_title
import org.qstuff.qplayer.resources.settings_dj_jogwheel_haptics_unavailable
import org.qstuff.qplayer.resources.settings_dj_stop_playback_on_cue_summary
import org.qstuff.qplayer.resources.settings_dj_stop_playback_on_cue_title
import org.qstuff.qplayer.resources.settings_privacy_crashreport_summary
import org.qstuff.qplayer.resources.settings_privacy_crashreport_title
import org.qstuff.qplayer.resources.settings_proceed_to_next_track_summary
import org.qstuff.qplayer.resources.settings_proceed_to_next_track_title
import org.qstuff.qplayer.resources.settings_show_clear_queue_summary
import org.qstuff.qplayer.resources.settings_show_clear_queue_title
import org.qstuff.qplayer.resources.settings_track_autostart_summary
import org.qstuff.qplayer.resources.settings_track_autostart_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    appVersion: String,
    deviceInfo: String,
    canVibrate: Boolean,
    onBack: () -> Unit
) {
    val state by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var showHapticsDialog by rememberSaveable { mutableStateOf(false) }

    // Index = JogwheelHaptics.LEVEL_* (off / light / medium / strong)
    val hapticEntries = stringArrayResource(Res.array.jogwheel_haptics_entries)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.more_menu_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            CategoryHeader(stringResource(Res.string.settings_cat_player_basic_title))
            SwitchRow(
                title = stringResource(Res.string.settings_track_autostart_title),
                summary = stringResource(Res.string.settings_track_autostart_summary),
                checked = state.autostart,
                onCheckedChange = settingsViewModel::setAutostart
            )
            SwitchRow(
                title = stringResource(Res.string.settings_proceed_to_next_track_title),
                summary = stringResource(Res.string.settings_proceed_to_next_track_summary),
                checked = state.proceedToNextTrack,
                onCheckedChange = settingsViewModel::setProceedToNextTrack
            )
            SwitchRow(
                title = stringResource(Res.string.settings_show_clear_queue_title),
                summary = stringResource(Res.string.settings_show_clear_queue_summary),
                checked = state.showClearQueueWarning,
                onCheckedChange = settingsViewModel::setShowClearQueueWarning
            )

            CategoryHeader(stringResource(Res.string.settings_cat_dj_specific_options_title))
            SwitchRow(
                title = stringResource(Res.string.settings_dj_stop_playback_on_cue_title),
                summary = stringResource(Res.string.settings_dj_stop_playback_on_cue_summary),
                checked = state.stopPlaybackOnCue,
                onCheckedChange = settingsViewModel::setStopPlaybackOnCue
            )
            // Jog wheel mode and sensitivity: quick switches in the jog wheel's corners
            // (JogQuickSwitches), no longer here.
            ClickableRow(
                title = stringResource(Res.string.settings_dj_jogwheel_haptics_title),
                summary = if (canVibrate) {
                    stringResource(
                        Res.string.settings_dj_jogwheel_haptics_summary,
                        hapticEntries.getOrElse(state.jogwheelHapticLevel) { "" }
                    )
                } else stringResource(Res.string.settings_dj_jogwheel_haptics_unavailable),
                enabled = canVibrate,
                onClick = { showHapticsDialog = true }
            )

            CategoryHeader(stringResource(Res.string.settings_cat_privacy_title))
            SwitchRow(
                title = stringResource(Res.string.settings_privacy_crashreport_title),
                summary = stringResource(Res.string.settings_privacy_crashreport_summary),
                checked = state.crashreporting,
                onCheckedChange = settingsViewModel::setCrashreporting
            )
            InfoRow(
                title = stringResource(Res.string.settings_app_version_title),
                value = appVersion
            )
            InfoRow(
                title = stringResource(Res.string.settings_device_info_title),
                value = deviceInfo
            )
        }
    }

    if (showHapticsDialog) {
        SingleChoiceDialog(
            title = stringResource(Res.string.settings_dj_jogwheel_haptics_title),
            entries = hapticEntries.toList(),
            selectedIndex = state.jogwheelHapticLevel,
            onSelect = { level ->
                settingsViewModel.setJogwheelHapticLevel(level)
                showHapticsDialog = false
            },
            onDismiss = { showHapticsDialog = false }
        )
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TitleAndSummary(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ClickableRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    TitleAndSummary(
        title = title,
        summary = summary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun InfoRow(title: String, value: String) {
    TitleAndSummary(
        title = title,
        summary = value,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun TitleAndSummary(title: String, summary: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    entries: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                entries.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = index == selectedIndex,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(entry, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        }
    )
}
