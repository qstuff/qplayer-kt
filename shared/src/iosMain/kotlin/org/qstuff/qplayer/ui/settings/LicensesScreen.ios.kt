package org.qstuff.qplayer.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import org.jetbrains.compose.resources.stringResource
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.more_menu_licenses

/**
 * The list comes from the iOS dependencies, exported at build time by the AboutLibraries plugin
 * (shared/build.gradle.kts) into the resources, so it can't go stale. Tapping an entry shows its
 * license.
 */
@Composable
actual fun LicensesScreen(onBack: () -> Unit) {
    SubScreenScaffold(title = stringResource(Res.string.more_menu_licenses), onBack = onBack) { padding ->
        val libraries by produceLibraries { Res.readBytes("files/aboutlibraries.json").decodeToString() }
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
