package org.qstuff.qplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
actual fun smallestScreenWidthDp(): Int = LocalConfiguration.current.smallestScreenWidthDp
