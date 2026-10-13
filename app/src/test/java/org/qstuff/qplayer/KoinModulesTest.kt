package org.qstuff.qplayer

import android.app.Application
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.datasource.room.RoomDataSource

class KoinModulesTest {

    /** Every ViewModel constructor parameter can be provided (data types come from dataModule). */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `view model module is complete`() {
        viewModelModule.verify(
            extraTypes = listOf(
                Application::class,
                PreferencesDataSource::class,
                RoomDataSource::class
            )
        )
    }
}
