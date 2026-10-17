package org.qstuff.qplayer.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.russhwolf.settings.NSUserDefaultsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.datasource.room.QDeqDatabase
import org.qstuff.qplayer.datasource.room.RoomDataSource
import org.qstuff.qplayer.platform.IosPlatformInfo
import org.qstuff.qplayer.platform.NoCrashReporter
import org.qstuff.qplayer.platform.NoHapticFeedback
import org.qstuff.qplayer.platform.NoScratcher
import org.qstuff.qplayer.platform.IosAudioPlayer
import org.qstuff.qplayer.platform.IosWaveformGenerator
import org.qstuff.qplayer.platform.appDataDirectory
import org.qstuff.qplayer.platform.IosPaths
import org.qstuff.qplayer.ui.filebrowser.PathStorageProvider
import org.qstuff.qplayer.ui.filebrowser.StorageProvider
import org.qstuff.qplayer.ui.filebrowser.StorageRoot
import org.qstuff.qplayer.ui.player.AudioPlayer
import org.qstuff.qplayer.ui.player.Scratcher
import org.qstuff.qplayer.ui.player.WaveformGenerator
import org.qstuff.qplayer.ui.player.jogwheel.HapticFeedback
import org.qstuff.qplayer.util.CrashReporter
import org.qstuff.qplayer.util.PlatformInfo
import platform.Foundation.NSUserDefaults

/** Persistence on iOS: settings in NSUserDefaults, the playlist database in Application Support. */
private val iosDataModule = module {
    single { PreferencesDataSource(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)) }
    single {
        Room.databaseBuilder<QDeqDatabase>(name = "${appDataDirectory()}/qplayer.db")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    singleOf(::RoomDataSource)
}

/**
 * iOS implementations of the platform interfaces: AVAudioEngine player and AVAudioFile waveform,
 * the file browser on the app's Documents folder. Still placeholders: scratching (Phase 3),
 * haptics, crash reporting.
 */
private val iosPlatformModule = module {
    factory<AudioPlayer> { IosAudioPlayer() }
    factory<Scratcher> { NoScratcher() }
    single<WaveformGenerator> { IosWaveformGenerator() }
    single<HapticFeedback> { NoHapticFeedback }
    single<CrashReporter> { NoCrashReporter }
    single<PlatformInfo> { IosPlatformInfo() }
    single<StorageProvider> {
        PathStorageProvider(
            findRoots = { listOf(StorageRoot(id = IosPaths.DOCUMENTS, label = "qdeq", isRemovable = false)) },
            toFilePath = IosPaths::toFilePath
        )
    }
}

/** All Koin modules of the iOS app (see MainViewController). */
val iosModules = listOf(iosDataModule, iosPlatformModule, viewModelModule)
