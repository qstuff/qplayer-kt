package org.qstuff.qplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val QOrange = Color(0xFFFC7614)
val QOrangeVariant = Color(0xFFAA4F0D)
val QBackground = Color(0xFF000000)
val QSurface = Color(0xFF1A1A1A)
val QSurfaceVariant = Color(0xFF2A2A2A)

private val DarkColorScheme = darkColorScheme(
    primary = QOrange,
    onPrimary = Color.Black,
    primaryContainer = QOrangeVariant,
    onPrimaryContainer = Color.White,
    secondary = QOrange,
    onSecondary = Color.Black,
    background = QBackground,
    onBackground = Color.White,
    surface = QSurface,
    onSurface = Color.White,
    surfaceVariant = QSurfaceVariant,
    onSurfaceVariant = Color(0xFFCCCCCC),
    error = Color(0xFFE60C00),
    onError = Color.White
)

@Composable
fun QDeqTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
