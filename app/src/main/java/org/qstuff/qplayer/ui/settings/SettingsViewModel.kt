package org.qstuff.qplayer.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.util.CrashReporting

data class SettingsUiState(
    val autostart: Boolean,
    val proceedToNextTrack: Boolean,
    val showClearQueueWarning: Boolean,
    val stopPlaybackOnCue: Boolean,
    val jogwheelSensitivity: Int,
    val jogwheelMode: Int,
    val jogwheelHapticLevel: Int,
    val crashreporting: Boolean
)

class SettingsViewModel : ViewModel(), KoinComponent {

    private val preferencesDataSource by inject<PreferencesDataSource>()

    private val _uiState = MutableStateFlow(readSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setAutostart(enabled: Boolean) {
        preferencesDataSource.setAutostartEnabled(enabled)
        _uiState.update { it.copy(autostart = enabled) }
    }

    fun setProceedToNextTrack(enabled: Boolean) {
        preferencesDataSource.setProceedToNextTrackEnabled(enabled)
        _uiState.update { it.copy(proceedToNextTrack = enabled) }
    }

    fun setShowClearQueueWarning(enabled: Boolean) {
        preferencesDataSource.setShowClearQueueWarningEnabled(enabled)
        _uiState.update { it.copy(showClearQueueWarning = enabled) }
    }

    fun setStopPlaybackOnCue(enabled: Boolean) {
        preferencesDataSource.setStopPlaybackOnSettingCuepointEnabled(enabled)
        _uiState.update { it.copy(stopPlaybackOnCue = enabled) }
    }

    fun setJogwheelSensitivity(sensitivity: Int) {
        preferencesDataSource.setJogWheelSensitivity(sensitivity)
        _uiState.update { it.copy(jogwheelSensitivity = sensitivity) }
    }

    fun setJogwheelMode(mode: Int) {
        preferencesDataSource.setJogWheelMode(mode)
        _uiState.update { it.copy(jogwheelMode = mode) }
    }

    fun setJogwheelHapticLevel(level: Int) {
        preferencesDataSource.setJogWheelHapticLevel(level)
        _uiState.update { it.copy(jogwheelHapticLevel = level) }
    }

    fun setCrashreporting(enabled: Boolean) {
        preferencesDataSource.setCrashreportingEnabled(enabled)
        CrashReporting.apply(enabled)
        _uiState.update { it.copy(crashreporting = enabled) }
    }

    private fun readSettings() = SettingsUiState(
        autostart = preferencesDataSource.isAutostartEnabled(),
        proceedToNextTrack = preferencesDataSource.isProceedToNextTrackEnabled(),
        showClearQueueWarning = preferencesDataSource.isShowClearQueueWarningEnabled(),
        stopPlaybackOnCue = preferencesDataSource.isStopPlaybackOnSettingCuepointEnabled(),
        jogwheelSensitivity = preferencesDataSource.getJogWheelSensitivity(),
        jogwheelMode = preferencesDataSource.getJogWheelMode(),
        jogwheelHapticLevel = preferencesDataSource.getJogWheelHapticLevel(),
        crashreporting = preferencesDataSource.isCrashreportingEnabled()
    )
}
