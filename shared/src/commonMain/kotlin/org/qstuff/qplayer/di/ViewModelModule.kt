package org.qstuff.qplayer.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.player.PlayerViewModel
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueViewModel
import org.qstuff.qplayer.ui.settings.SettingsViewModel

/** The app's ViewModels, created by Koin (constructor injection). */
val viewModelModule = module {
    viewModelOf(::PlayerViewModel)
    viewModelOf(::QueueViewModel)
    viewModelOf(::PlaylistViewModel)
    viewModelOf(::FileBrowserViewModel)
    viewModelOf(::SettingsViewModel)
}
