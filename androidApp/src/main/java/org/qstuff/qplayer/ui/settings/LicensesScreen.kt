package org.qstuff.qplayer.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import org.qstuff.qplayer.R

/**
 * Open source licenses of all libraries the app uses. The list is generated at build time by the
 * AboutLibraries Gradle plugin from the actual dependencies (plus config/libraries/ for code copied
 * into the app), so it can't go stale. Tapping an entry shows its full license text.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    SubScreenScaffold(title = stringResource(R.string.more_menu_licenses), onBack = onBack) { padding ->
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
