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

        // Saved/Loaded in FileBrowserModel
        const val PREF_LAST_BROWSED_DIR = "PREF_LAST_BROWSED_DIR"
        const val DEFAULT_ROOT_DIR = "/storage/"

        // Saved/Loaded in QueueViewModel
        const val PREF_QUEUE_LIST = "PREF_QUEUE_LIST"
        const val PREF_SELECTED_TRACK = "PREF_SELECTED_TRACK"

        const val PREF_SHUFFLE_MODE = "PREF_SHUFFLE_MODE"
        const val PREF_REPEAT_MODE = "PREF_REPEAT_MODE"
        const val PREF_AUTOPLAY_MODE = "PREF_AUTOPLAY_MODE"

        // Saved/Loaded in PlayerViewModel
        const val PREF_TRACK_POSITION = "PREF_TRACK_POSITION"
        const val PREF_MASTER_TEMPO_MODE = "PREF_MASTER_TEMPO_MODE"

    }

    private val preferences: SharedPreferences = context.getSharedPreferences("QDEQ", Context.MODE_PRIVATE)

    fun getRootDir() = DEFAULT_ROOT_DIR

    fun saveLastBrowsedDir(dir: String) =
        preferences.edit {
            putString(PREF_LAST_BROWSED_DIR, dir)
        }

    fun getLastBrowsedDir() = preferences.getString(PREF_LAST_BROWSED_DIR, DEFAULT_ROOT_DIR)

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
        return Gson().fromJson<ArrayList<Track>>(json, object: TypeToken<ArrayList<Track>>() {}.type);
    }

    fun saveSelectedTrackList(track: Track) =
        preferences.edit {
            putString(PREF_SELECTED_TRACK, Gson().toJson(track))
        }

    fun readSelectedTrack(): Track? {
        val json = preferences.getString(PREF_SELECTED_TRACK, "")
        if (json.isNullOrBlank()) return null
        return Gson().fromJson<Track>(json, object: TypeToken<Track>() {}.type);
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


}