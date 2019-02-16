package org.qstuff.qplayer.datasource.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.koin.standalone.KoinComponent
import org.qstuff.qplayer.datasource.model.Track

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class PreferencesDataSource (val context: Context) : KoinComponent {

    companion object {
        const val PREF_LAST_BROWSED_DIR = "PREF_LAST_BROWSED_DIR"
        const val DEFAULT_ROOT_DIR = "/storage/"

        const val PREF_QUEUE_LIST = "PREF_QUEUE_LIST"

    }

    private val preferences: SharedPreferences = context.getSharedPreferences("QDEQ", Context.MODE_PRIVATE)

    fun getRootDir() = DEFAULT_ROOT_DIR

    fun saveLastBrowsedDir(dir: String) =
        preferences.edit {
            putString(PREF_LAST_BROWSED_DIR, dir)
        }

    fun getLastBrowsedDir() = preferences.getString(PREF_LAST_BROWSED_DIR, DEFAULT_ROOT_DIR)

    fun saveTrackList(key: String, tracks: ArrayList<Track>) =
        preferences.edit {
            putString(key, Gson().toJson(tracks))
        }

    fun readTrackList(key: String): ArrayList<Track>? {

        val json = preferences.getString(key, "")
        if (json.isNullOrBlank()) return null
        return Gson().fromJson<ArrayList<Track>>(json, object: TypeToken<ArrayList<Track>>() {}.type);
    }
}