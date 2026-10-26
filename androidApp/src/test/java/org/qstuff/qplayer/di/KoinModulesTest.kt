package org.qstuff.qplayer.di

import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.datasource.room.PlaylistStore
import org.qstuff.qplayer.ui.filebrowser.StorageProvider
import org.qstuff.qplayer.ui.player.AudioPlayer
import org.qstuff.qplayer.ui.player.WaveformGenerator
import org.qstuff.qplayer.ui.player.Scratcher
import org.qstuff.qplayer.util.CrashReporter

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
                PlaylistStore::class,
                AudioPlayer::class,
                Scratcher::class,
                WaveformGenerator::class,
                StorageProvider::class,
                CrashReporter::class
            )
        )
    }
}
