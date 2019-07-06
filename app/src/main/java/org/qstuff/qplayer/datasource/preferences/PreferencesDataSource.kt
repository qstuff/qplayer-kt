package org.qstuff.qplayer.datasource.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.koin.standalone.KoinComponent
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.util.TrackRepeatStatus

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class PreferencesDataSource (val context: Context) : KoinComponent {

    companion object {

        // Saved/Loaded states in FileBrowserModel
        const val PREF_LAST_BROWSED_DIR = "PREF_LAST_BROWSED_DIR"
        const val DEFAULT_ROOT_DIR = "/storage/"

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

        // From SettingsFragment
        const val PREFS_TRACK_AUTOSTART = "PREFS_TRACK_AUTOSTART"
        const val PREFS_PROCEED_TO_NEXT_TRACK = "PREFS_PROCEED_TO_NEXT_TRACK"
        const val PREFS_SKIP_BACK_TO_START = "PREFS_SKIP_BACK_TO_START"
        const val PREFS_STOP_PLAYBACK_ON_CUE = "PREFS_STOP_PLAYBACK_ON_CUE"
        //const val PREFS_ENABLE_CUE = "PREFS_ENABLE_CUE"
        const val PREFS_ENABLE_CRASHREPORTING = "PREFS_ENABLE_CRASHREPORTING"
        const val PREFS_ENABLE_REMAIN_BLINK = "PREFS_ENABLE_REMAIN_BLINK"
        const val PREFS_SHOW_CLEAR_QUEUE_DIALOG = "PREFS_SHOW_CLEAR_QUEUE_DIALOG"
        const val PREFS_JOG_WHEEL_SENSITIVITY = "PREFS_JOG_WHEEL_SENSITIVITY"


        // Others
        const val PREFS_ENABLE_CRASHREPORTING_DIALOG_SHOWN = "PREFS_ENABLE_CRASHREPORTING_DIALOG_SHOWN"
    }


    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences("QDEQ", Context.MODE_PRIVATE)

    fun getRootDir() = DEFAULT_ROOT_DIR

    fun saveLastBrowsedDir(dir: String) =
        preferences.edit {
            putString(PREF_LAST_BROWSED_DIR, dir)
        }

    fun getLastBrowsedDir(): String? = preferences.getString(PREF_LAST_BROWSED_DIR, DEFAULT_ROOT_DIR)

    //
    // Saved/Loaded in QueueViewModel
    //

    fun saveTrackList(key: String, tracks: ArrayList<Track>) =
        preferences.edit {
            putString(key, Gson().toJson(tracks))
        }

    fun readTrackList(key: String): ArrayList<Track>? {

        val json = preferences.getString(key, "")
        if (json.isNullOrBlank()) return null
        return Gson().fromJson<ArrayList<Track>>(json, object: TypeToken<ArrayList<Track>>() {}.type)
    }

    fun saveSelectedTrackList(track: Track) =
        preferences.edit {
            putString(PREF_SELECTED_TRACK, Gson().toJson(track))
        }

    fun readSelectedTrack(): Track? {
        val json = preferences.getString(PREF_SELECTED_TRACK, "")
        if (json.isNullOrBlank()) return null
        return Gson().fromJson<Track>(json, object: TypeToken<Track>() {}.type)
    }

    fun saveShuffleMode(shuffle: Boolean) =
        preferences.edit{
            putBoolean(PREF_SHUFFLE_MODE, shuffle)
        }

    fun readShuffleMode() = preferences.getBoolean(PREF_SHUFFLE_MODE, false)

    fun saveRepeatMode(repeat: Int) =
            preferences.edit{
                putInt(PREF_REPEAT_MODE, repeat)
            }

    fun readRepeatMode() = preferences.getInt(PREF_REPEAT_MODE, TrackRepeatStatus.NONE.ordinal)

    //
    // Saved/Loaded in PlayerModel
    //

    fun savePitchFactorIndex(pitchFactorIndex: Int) =
            preferences.edit{
                putInt(PREF_PITCH_FACTOR_INDEX, pitchFactorIndex)
            }

    fun readPitchFactorIndex() = preferences.getInt(PREF_PITCH_FACTOR_INDEX, 0)

    fun savePitchValue(pitchValue: Int) =
            preferences.edit{
                putInt(PREF_PITCH_VALUE, pitchValue)
            }

    fun readPitchValue() = preferences.getInt(PREF_PITCH_VALUE, 500)

    fun saveMasterTempoMode(enabled: Boolean) =
            preferences.edit{
                putBoolean(PREF_MASTER_TEMPO_MODE, enabled)
            }


    fun readRemainigTimeMode() = preferences.getBoolean(PREF_SHOW_REMAINING, true)

    fun saveRemainigTimeMode(enabled: Boolean) =
            preferences.edit{
                putBoolean(PREF_SHOW_REMAINING, enabled)
            }


    fun readMasterTempoMode() = preferences.getBoolean(PREF_MASTER_TEMPO_MODE, false)

    //
    // Others
    //

    fun isCrashreportingEnabledDialogShown() = preferences.getBoolean(PREFS_ENABLE_CRASHREPORTING_DIALOG_SHOWN, false)
    fun setCrashreportingEnabledDialogShown(shown: Boolean) =
            preferences.edit {
                putBoolean(PREFS_ENABLE_CRASHREPORTING_DIALOG_SHOWN, shown)
            }
    //
    // SettingsFragment
    //

    fun isAutostartEnabled() = preferences.getBoolean(PREFS_TRACK_AUTOSTART, false)
    fun isProceedToNextTrackEnabled() = preferences.getBoolean(PREFS_PROCEED_TO_NEXT_TRACK, true)
    fun isSkipBackToStartEnabled() = preferences.getBoolean(PREFS_SKIP_BACK_TO_START, true)
    fun isShowClearQueueWarningEnabled() = preferences.getBoolean(PREFS_SHOW_CLEAR_QUEUE_DIALOG, true)
    fun isStopPlaybackOnSettingCuepointEnabled() = preferences.getBoolean(PREFS_STOP_PLAYBACK_ON_CUE, false)
    fun isCrashreportingEnabled() = preferences.getBoolean(PREFS_ENABLE_CRASHREPORTING, false)
    fun isBlinkingRemainEnabled() = preferences.getBoolean(PREFS_ENABLE_REMAIN_BLINK, true)
    fun getJogWheelSensitivity() = Integer.parseInt(preferences.getString(PREFS_JOG_WHEEL_SENSITIVITY, "10")!!)

}