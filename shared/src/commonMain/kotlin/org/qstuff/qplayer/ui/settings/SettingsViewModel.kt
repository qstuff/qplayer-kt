package org.qstuff.qplayer.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.util.CrashReporter

data class SettingsUiState(
    val autostart: Boolean,
    val proceedToNextTrack: Boolean,
    val showClearQueueWarning: Boolean,
    val stopPlaybackOnCue: Boolean,
    val jogwheelHapticLevel: Int,
    val crashreporting: Boolean
)

class SettingsViewModel(
    private val preferencesDataSource: PreferencesDataSource,
    private val crashReporter: CrashReporter
) : ViewModel() {


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

    fun setJogwheelHapticLevel(level: Int) {
        preferencesDataSource.setJogWheelHapticLevel(level)
        _uiState.update { it.copy(jogwheelHapticLevel = level) }
    }

    fun setCrashreporting(enabled: Boolean) {
        preferencesDataSource.setCrashreportingEnabled(enabled)
        crashReporter.setEnabled(enabled)
        _uiState.update { it.copy(crashreporting = enabled) }
    }

    private fun readSettings() = SettingsUiState(
        autostart = preferencesDataSource.isAutostartEnabled(),
        proceedToNextTrack = preferencesDataSource.isProceedToNextTrackEnabled(),
        showClearQueueWarning = preferencesDataSource.isShowClearQueueWarningEnabled(),
        stopPlaybackOnCue = preferencesDataSource.isStopPlaybackOnSettingCuepointEnabled(),
        jogwheelHapticLevel = preferencesDataSource.getJogWheelHapticLevel(),
        crashreporting = preferencesDataSource.isCrashreportingEnabled()
    )
}
