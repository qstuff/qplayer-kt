package org.qstuff.qplayer.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.more_menu_licenses

// TODO Phase 2: the AboutLibraries list (its JSON exported into the resources) like on Android.
@Composable
actual fun LicensesScreen(onBack: () -> Unit) {
    SubScreenScaffold(title = stringResource(Res.string.more_menu_licenses), onBack = onBack) { padding ->
        Text(
            text = "Coming soon on iOS.",
            color = Color.White,
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        )
    }
}
