package org.qstuff.qplayer.ui.settings

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import org.qstuff.qplayer.R
import org.qstuff.qplayer.ui.lockOrientationForDevice
import org.qstuff.qplayer.ui.theme.QDeqTheme

/**
 * Open source licenses of all libraries the app uses. The list is generated at build time by the
 * AboutLibraries Gradle plugin from the actual dependencies (plus config/libraries/ for code copied
 * into the app), so it can't go stale. Tapping an entry shows its full license text.
 */
class LicensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockOrientationForDevice(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)

        setContent {
            QDeqTheme {
                LicensesScreen(onBack = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_menu_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
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
