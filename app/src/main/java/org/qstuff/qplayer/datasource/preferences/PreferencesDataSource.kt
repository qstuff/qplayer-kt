package org.qstuff.qplayer.datasource.preferences

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.util.JogwheelMode
import org.qstuff.qplayer.util.TrackRepeatStatus

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
/**
 * All persisted settings and player/queue state, on a multiplatform [Settings] store (on Android
 * the SharedPreferences file "QDEQ" — same keys and value formats as before, so nothing needs
 * migrating). The queue and the selected track are stored as JSON.
 */
class PreferencesDataSource(private val settings: Settings) {

    companion object {

        // Saved/Loaded states in FileBrowserModel
        const val PREF_LAST_BROWSED_DIR = "PREF_LAST_BROWSED_DIR"
        const val DEFAULT_ROOT_DIR = "/storage/emulated/0"

        // Saved/Loaded states in QueueViewModel
        const val PREF_QUEUE_LIST = "PREF_QUEUE_LIST"
        const val PREF_SELECTED_TRACK = "PREF_SELECTED_TRACK"
        const val PREF_SHUFFLE_MODE = "PREF_SHUFFLE_MODE"
        const val PREF_REPEAT_MODE = "PREF_REPEAT_MODE"

        // Saved/Loaded states in PlayerViewModel
        const val PREF_TRACK_POSITION = "PREF_TRACK_POSITION"
        const val PREF_MASTER_TEMPO_MODE = "PREF_MASTER_TEMPO_MODE"
        const val PREF_PITCH_FACTOR_INDEX = "PREF_PITCH_FACTOR_INDEX"
        const val PREF_PITCH_VALUE = "PREF_PITCH_VALUE"
        const val PREF_SHOW_REMAINING = "PREF_SHOW_REMAINING"

        // Settings
        const val PREFS_TRACK_AUTOSTART = "PREFS_TRACK_AUTOSTART"
        const val PREFS_PROCEED_TO_NEXT_TRACK = "PREFS_PROCEED_TO_NEXT_TRACK"
        const val PREFS_STOP_PLAYBACK_ON_CUE = "PREFS_STOP_PLAYBACK_ON_CUE"
        const val PREFS_ENABLE_CRASHREPORTING = "PREFS_ENABLE_CRASHREPORTING"
        const val PREFS_SHOW_CLEAR_QUEUE_DIALOG = "PREFS_SHOW_CLEAR_QUEUE_DIALOG"
        const val PREFS_JOG_WHEEL_SENSITIVITY = "PREFS_JOG_WHEEL_SENSITIVITY"
        const val PREFS_JOG_WHEEL_MODE = "PREFS_JOG_WHEEL_MODE"
        const val PREFS_JOG_WHEEL_HAPTICS = "PREFS_JOG_WHEEL_HAPTICS"

        // Others
        const val PREFS_ENABLE_CRASHREPORTING_DIALOG_SHOWN = "PREFS_ENABLE_CRASHREPORTING_DIALOG_SHOWN"
    }


    private val log = Logger.withTag("PreferencesDataSource")

    // Also reads queues stored by the former Gson code: they contain Track.trackStatus, which is
    // no longer persisted.
    private val json = Json { ignoreUnknownKeys = true }

    fun saveLastBrowsedDir(dir: String) =
        settings.putString(PREF_LAST_BROWSED_DIR, dir)

    fun getLastBrowsedDir(): String =
            settings.getString(PREF_LAST_BROWSED_DIR, DEFAULT_ROOT_DIR)

    //
    // Saved/Loaded in QueueViewModel
    //

    fun saveTrackList(key: String, tracks: ArrayList<Track>) =
        settings.putString(key, json.encodeToString(ListSerializer(Track.serializer()), tracks))

    fun readTrackList(key: String): ArrayList<Track>? {
        val stored = settings.getString(key, "")
        if (stored.isBlank()) return null
        return runCatching { ArrayList(json.decodeFromString(ListSerializer(Track.serializer()), stored)) }
            .onFailure { log.e(it) { "readTrackList(): can't parse the stored queue" } }
            .getOrNull()
    }

    fun saveSelectedTrackList(track: Track) =
        settings.putString(PREF_SELECTED_TRACK, json.encodeToString(Track.serializer(), track))

    fun readSelectedTrack(): Track? {
        val stored = settings.getString(PREF_SELECTED_TRACK, "")
        if (stored.isBlank()) return null
        return runCatching { json.decodeFromString(Track.serializer(), stored) }
            .onFailure { log.e(it) { "readSelectedTrack(): can't parse the stored track" } }
            .getOrNull()
    }

    fun saveShuffleMode(shuffle: Boolean) =
        settings.putBoolean(PREF_SHUFFLE_MODE, shuffle)

    fun readShuffleMode() = settings.getBoolean(PREF_SHUFFLE_MODE, false)

    fun saveRepeatMode(repeat: Int) =
            settings.putInt(PREF_REPEAT_MODE, repeat)

    fun readRepeatMode() = settings.getInt(PREF_REPEAT_MODE, TrackRepeatStatus.NONE.ordinal)

    //
    // Saved/Loaded in PlayerModel
    //

    fun savePitchFactorIndex(pitchFactorIndex: Int) =
            settings.putInt(PREF_PITCH_FACTOR_INDEX, pitchFactorIndex)

    fun readPitchFactorIndex() = settings.getInt(PREF_PITCH_FACTOR_INDEX, 0)

    fun savePitchValue(pitchValue: Int) =
            settings.putInt(PREF_PITCH_VALUE, pitchValue)

    fun readPitchValue() = settings.getInt(PREF_PITCH_VALUE, 500)

    fun saveMasterTempoMode(enabled: Boolean) =
            settings.putBoolean(PREF_MASTER_TEMPO_MODE, enabled)

    fun readMasterTempoMode() = settings.getBoolean(PREF_MASTER_TEMPO_MODE, false)

    fun saveRemainingTimeMode(enabled: Boolean) =
            settings.putBoolean(PREF_SHOW_REMAINING, enabled)

    fun readRemainingTimeMode() = settings.getBoolean(PREF_SHOW_REMAINING, true)

    //
    // Others
    //

    /** Whether the crash reporting opt-in dialog was answered (it's shown once). */
    fun isCrashreportingEnabledDialogShown() = settings.getBoolean(PREFS_ENABLE_CRASHREPORTING_DIALOG_SHOWN, false)
    fun setCrashreportingEnabledDialogShown(shown: Boolean) =
            settings.putBoolean(PREFS_ENABLE_CRASHREPORTING_DIALOG_SHOWN, shown)
    //
    // Settings
    //

    fun isAutostartEnabled() = settings.getBoolean(PREFS_TRACK_AUTOSTART, false)
    fun isProceedToNextTrackEnabled() = settings.getBoolean(PREFS_PROCEED_TO_NEXT_TRACK, true)
    fun isShowClearQueueWarningEnabled() = settings.getBoolean(PREFS_SHOW_CLEAR_QUEUE_DIALOG, true)
    fun isStopPlaybackOnSettingCuepointEnabled() = settings.getBoolean(PREFS_STOP_PLAYBACK_ON_CUE, false)
    fun isCrashreportingEnabled() = settings.getBoolean(PREFS_ENABLE_CRASHREPORTING, false)
    fun getJogWheelSensitivity() = settings.getString(PREFS_JOG_WHEEL_SENSITIVITY, "10").toInt()
    fun getJogWheelMode() = settings.getString(PREFS_JOG_WHEEL_MODE, "0").toInt()
    /** JogwheelHaptics.LEVEL_* (off / light / medium / strong); medium by default. */
    fun getJogWheelHapticLevel() = settings.getInt(PREFS_JOG_WHEEL_HAPTICS, 2)
    fun getJogWheelModeEnum() = JogwheelMode.entries[settings.getString(PREFS_JOG_WHEEL_MODE, "0").toInt()]

    fun setAutostartEnabled(enabled: Boolean) =
            settings.putBoolean(PREFS_TRACK_AUTOSTART, enabled)

    fun setProceedToNextTrackEnabled(enabled: Boolean) =
            settings.putBoolean(PREFS_PROCEED_TO_NEXT_TRACK, enabled)

    fun setShowClearQueueWarningEnabled(enabled: Boolean) =
            settings.putBoolean(PREFS_SHOW_CLEAR_QUEUE_DIALOG, enabled)

    fun setStopPlaybackOnSettingCuepointEnabled(enabled: Boolean) =
            settings.putBoolean(PREFS_STOP_PLAYBACK_ON_CUE, enabled)

    fun setCrashreportingEnabled(enabled: Boolean) =
            settings.putBoolean(PREFS_ENABLE_CRASHREPORTING, enabled)

    // Jog wheel values are stored as Strings (format of the former ListPreference)
    fun setJogWheelSensitivity(sensitivity: Int) =
            settings.putString(PREFS_JOG_WHEEL_SENSITIVITY, sensitivity.toString())

    fun setJogWheelMode(mode: Int) =
            settings.putString(PREFS_JOG_WHEEL_MODE, mode.toString())

    fun setJogWheelHapticLevel(level: Int) =
            settings.putInt(PREFS_JOG_WHEEL_HAPTICS, level)
}