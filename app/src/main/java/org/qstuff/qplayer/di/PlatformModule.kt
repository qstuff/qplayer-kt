package org.qstuff.qplayer.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.qstuff.qplayer.ui.filebrowser.FileStorageProvider
import org.qstuff.qplayer.ui.filebrowser.StorageProvider
import org.qstuff.qplayer.ui.filebrowser.StorageRoots
import org.qstuff.qplayer.ui.player.AudioPlayer
import org.qstuff.qplayer.ui.player.Scratcher
import org.qstuff.qplayer.ui.player.WaveformGenerator
import org.qstuff.qplayer.ui.player.jogwheel.AndroidHapticFeedback
import org.qstuff.qplayer.ui.player.jogwheel.HapticFeedback
import org.qstuff.qplayer.ui.player.mediaservice.MediaServiceAudioPlayer
import org.qstuff.qplayer.ui.player.scratch.ScratchEngine
import org.qstuff.qplayer.ui.player.waveform.AndroidWaveformGenerator
import org.qstuff.qplayer.util.AndroidPlatformInfo
import org.qstuff.qplayer.util.CrashReporter
import org.qstuff.qplayer.util.FirebaseCrashReporter
import org.qstuff.qplayer.util.PlatformInfo

/** Android implementations of the platform interfaces the ViewModels use. */
val platformModule = module {
    // One engine / scratch engine per PlayerViewModel (activity-scoped).
    factory<AudioPlayer> { MediaServiceAudioPlayer(androidContext()) }
    factory<Scratcher> { ScratchEngine(androidContext()) }
    single<WaveformGenerator> { AndroidWaveformGenerator(androidContext()) }
    single<HapticFeedback> { AndroidHapticFeedback(androidContext()) }
    single<CrashReporter> { FirebaseCrashReporter() }
    single<PlatformInfo> { AndroidPlatformInfo(androidContext()) }
    single<StorageProvider> {
        val context = androidContext()
        FileStorageProvider { StorageRoots.find(context) }
    }
}
