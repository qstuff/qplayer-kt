package org.qstuff.qplayer.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import org.jetbrains.compose.resources.stringResource
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.more_menu_licenses

/**
 * The list is generated at build time by the AboutLibraries Gradle plugin (in :androidApp) from the
 * actual dependencies (plus config/libraries/ for code copied into the app), so it can't go stale.
 * Tapping an entry shows its full license text.
 */
@Composable
actual fun LicensesScreen(onBack: () -> Unit) {
    SubScreenScaffold(title = stringResource(Res.string.more_menu_licenses), onBack = onBack) { padding ->
        // Loaded off the main thread from the JSON the plugin generated at build time.
        val libraries by produceLibraries()
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
