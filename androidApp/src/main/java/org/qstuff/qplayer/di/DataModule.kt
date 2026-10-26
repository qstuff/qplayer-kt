package org.qstuff.qplayer.di

import android.content.Context
import androidx.room.Room
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.datasource.room.QDeqDatabase
import org.qstuff.qplayer.datasource.room.PlaylistStore
import org.qstuff.qplayer.datasource.room.RoomDataSource

/** Persistence: settings / player state (SharedPreferences "QDEQ") and the playlist database. */
val dataModule = module {
    single {
        PreferencesDataSource(
            SharedPreferencesSettings(androidContext().getSharedPreferences("QDEQ", Context.MODE_PRIVATE))
        )
    }
    single {
        Room.databaseBuilder(androidContext(), QDeqDatabase::class.java, "qplayer").build()
    }
    singleOf(::RoomDataSource) bind PlaylistStore::class
}
