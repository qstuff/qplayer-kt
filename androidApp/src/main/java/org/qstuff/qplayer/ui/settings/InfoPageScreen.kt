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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.qstuff.qplayer.ui.theme.QOrange

/**
 * A text page (privacy statement, imprint) from a simple HTML file in the assets, rendered with
 * Compose — no WebView. Links (https, mailto) open in the matching app.
 */
@Composable
fun InfoPageScreen(title: String, assetName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val html by produceState(initialValue = "", assetName) {
        value = withContext(Dispatchers.IO) {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }
    }
    val text = remember(html) {
        AnnotatedString.fromHtml(
            // The file header comment is for editors only.
            htmlString = html.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), ""),
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
