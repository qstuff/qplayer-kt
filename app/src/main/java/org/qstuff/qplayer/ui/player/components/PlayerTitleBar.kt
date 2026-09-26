package org.qstuff.qplayer.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.qstuff.qplayer.R
import org.qstuff.qplayer.ui.theme.QOrange

/**
 * Top title bar: the "qdeq" wordmark (+ optional version [titleSuffix]) and a "more" overflow
 * menu (Settings / Privacy / Imprint / Licenses). The menu's open state is local to the bar.
 */
@Composable
fun PlayerTitleBar(
    titleSuffix: String,
    onOpenSettings: () -> Unit,
    onOpenWebView: (url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val roundedShape = RoundedCornerShape(dimensionResource(R.dimen.rounded_shape_radius))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.title_textview_height))
            .background(Color.Black, roundedShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = QOrange)) { append("q") }
                withStyle(SpanStyle(color = Color.White)) { append("deq") }
                if (titleSuffix.isNotEmpty()) {
                    withStyle(SpanStyle(color = Color.White)) { append(titleSuffix) }
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = dimensionResource(R.dimen.textview_padding_start))
        )
        Box {
            IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier.size(dimensionResource(R.dimen.kebabbutton_size))
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert_white_24dp),
                    contentDescription = "More",
                    tint = Color.White
                )
            }
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = { showMoreMenu = false; onOpenSettings() }
                )
                DropdownMenuItem(
                    text = { Text("Privacy") },
                    onClick = { showMoreMenu = false; onOpenWebView("privacy.html") }
                )
                DropdownMenuItem(
                    text = { Text("Imprint") },
                    onClick = { showMoreMenu = false; onOpenWebView("imprint.html") }
                )
                DropdownMenuItem(
                    text = { Text("Licenses") },
                    onClick = { showMoreMenu = false; onOpenWebView("licenses.html") }
                )
            }
        }
    }
}
