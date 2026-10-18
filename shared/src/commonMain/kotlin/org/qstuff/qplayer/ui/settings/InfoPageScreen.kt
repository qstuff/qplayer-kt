package org.qstuff.qplayer.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.ui.theme.QOrange

/**
 * A text page (privacy statement, imprint) from a simple HTML file in the resources ([file], e.g.
 * "files/privacy.html"), rendered with Compose via [simpleHtmlToAnnotatedString] — no WebView.
 * Links (https, mailto) open in the matching app.
 */
@Composable
fun InfoPageScreen(title: String, file: String, onBack: () -> Unit) {
    val html by produceState(initialValue = "", file) {
        value = Res.readBytes(file).decodeToString()
    }
    val text = remember(html) {
        simpleHtmlToAnnotatedString(
            html,
            linkStyles = TextLinkStyles(
                style = SpanStyle(color = QOrange, textDecoration = TextDecoration.Underline)
            )
        )
    }

    SubScreenScaffold(title = title, onBack = onBack) { padding ->
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
