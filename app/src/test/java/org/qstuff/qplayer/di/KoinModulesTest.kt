package org.qstuff.qplayer.di

import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.datasource.room.RoomDataSource
import org.qstuff.qplayer.ui.filebrowser.StorageProvider
import org.qstuff.qplayer.ui.player.AudioPlayer
import org.qstuff.qplayer.ui.player.WaveformGenerator
import org.qstuff.qplayer.ui.player.scratch.ScratchEngine

class KoinModulesTest {

    /**
     * Every ViewModel constructor parameter can be provided (data types come from dataModule, the
     * platform implementations from platformModule).
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `view model module is complete`() {
        viewModelModule.verify(
            extraTypes = listOf(
                PreferencesDataSource::class,
                RoomDataSource::class,
                AudioPlayer::class,
                ScratchEngine::class,
                WaveformGenerator::class,
                StorageProvider::class
            )
        )
    }
}
