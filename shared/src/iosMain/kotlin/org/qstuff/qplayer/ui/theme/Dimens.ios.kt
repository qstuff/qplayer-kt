package org.qstuff.qplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
actual fun smallestScreenWidthDp(): Int {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { minOf(size.width, size.height).toDp().value.toInt() }
}
