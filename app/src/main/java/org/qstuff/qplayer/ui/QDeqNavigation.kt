package org.qstuff.qplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.qstuff.qplayer.R
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.player.PlayerScreen
import org.qstuff.qplayer.ui.player.PlayerViewModel
import org.qstuff.qplayer.ui.player.jogwheel.JogwheelHaptics
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueViewModel
import org.qstuff.qplayer.ui.settings.InfoPageScreen
import org.qstuff.qplayer.ui.settings.LicensesScreen
import org.qstuff.qplayer.ui.settings.SettingsScreen
import org.qstuff.qplayer.ui.settings.appVersionString
import org.qstuff.qplayer.ui.settings.deviceInfoString

/** The app's screens — the keys on the Navigation 3 back stack. */
@Serializable
sealed interface Screen : NavKey {
    @Serializable data object Player : Screen
    @Serializable data object Settings : Screen
    @Serializable data object Licenses : Screen
    @Serializable data object Privacy : Screen
    @Serializable data object Imprint : Screen
}

// The back stack is saved via kotlinx.serialization. Registering every key explicitly (instead of
// the reflection-based Android variant) also works on iOS later.
private val navSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Screen.Player::class)
            subclass(Screen.Settings::class)
            subclass(Screen.Licenses::class)
            subclass(Screen.Privacy::class)
            subclass(Screen.Imprint::class)
        }
    }
}

/**
 * The app's single navigation host: the player is the start screen, Settings / Licenses / the info
 * pages are pushed on top of it. Back on the player screen isn't handled here, so it still closes
 * the activity (which stops playback, as before).
 *
 * The player ViewModels are activity-scoped (the activity wires them together); Settings gets its
 * own ViewModel, cleared when the screen leaves the back stack.
 *
 * @param onSettingsClosed leaving Settings: apply changed settings to the player.
 */
@Composable
fun QDeqNavigation(
    playerViewModel: PlayerViewModel,
    queueViewModel: QueueViewModel,
    playlistViewModel: PlaylistViewModel,
    fileBrowserViewModel: FileBrowserViewModel,
    titleSuffix: String,
    onSettingsClosed: () -> Unit
) {
    val backStack = rememberNavBackStack(navSavedStateConfiguration, Screen.Player)
    val navigateBack: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        onBack = navigateBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Screen.Player> {
                PlayerScreen(
                    playerViewModel = playerViewModel,
                    queueViewModel = queueViewModel,
                    playlistViewModel = playlistViewModel,
                    fileBrowserViewModel = fileBrowserViewModel,
                    titleSuffix = titleSuffix,
                    onOpenSettings = { backStack.add(Screen.Settings) },
                    onOpenPrivacy = { backStack.add(Screen.Privacy) },
                    onOpenImprint = { backStack.add(Screen.Imprint) },
                    onOpenLicenses = { backStack.add(Screen.Licenses) }
                )
            }
            entry<Screen.Settings> {
                // However Settings is left (back arrow, system back), the player picks up changes.
                DisposableEffect(Unit) { onDispose(onSettingsClosed) }
                val context = LocalContext.current
                SettingsScreen(
                    settingsViewModel = viewModel(),
                    appVersion = remember { context.appVersionString() },
                    deviceInfo = remember { deviceInfoString() },
                    canVibrate = remember { JogwheelHaptics.isSupported(context) },
                    onBack = navigateBack
                )
            }
            entry<Screen.Licenses> {
                LicensesScreen(onBack = navigateBack)
            }
            entry<Screen.Privacy> {
                InfoPageScreen(
                    title = stringResource(R.string.more_menu_privacy),
                    assetName = "privacy.html",
                    onBack = navigateBack
                )
            }
            entry<Screen.Imprint> {
                InfoPageScreen(
                    title = stringResource(R.string.more_menu_imprint),
                    assetName = "imprint.html",
                    onBack = navigateBack
                )
            }
        }
    )
}
