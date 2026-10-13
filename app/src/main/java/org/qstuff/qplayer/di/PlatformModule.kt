package org.qstuff.qplayer.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.qstuff.qplayer.ui.player.AudioPlayer
import org.qstuff.qplayer.ui.player.WaveformGenerator
import org.qstuff.qplayer.ui.player.mediaservice.MediaServiceAudioPlayer
import org.qstuff.qplayer.ui.player.scratch.ScratchEngine
import org.qstuff.qplayer.ui.player.waveform.AndroidWaveformGenerator

/** Android implementations of the platform interfaces the ViewModels use. */
val platformModule = module {
    // One engine / scratch engine per PlayerViewModel (activity-scoped).
    factory<AudioPlayer> { MediaServiceAudioPlayer(androidContext()) }
    factory { ScratchEngine(androidContext()) }
    single<WaveformGenerator> { AndroidWaveformGenerator(androidContext()) }
}
