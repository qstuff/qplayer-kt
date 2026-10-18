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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.qstuff.qplayer.ui.theme.QOrange
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.qstuff.qplayer.resources.Res
import org.qstuff.qplayer.resources.ic_more_vert_white_24dp
import org.qstuff.qplayer.resources.more_menu_imprint
import org.qstuff.qplayer.resources.more_menu_licenses
import org.qstuff.qplayer.resources.more_menu_privacy
import org.qstuff.qplayer.resources.more_menu_settings
import org.qstuff.qplayer.ui.theme.LocalDimens

/**
 * Top title bar: the "QdeQ" wordmark (+ optional version [titleSuffix]) and a "more" overflow
 * menu (Settings / Privacy / Imprint / Licenses). The menu's open state is local to the bar.
 */
@Composable
fun PlayerTitleBar(
    titleSuffix: String,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenImprint: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val roundedShape = RoundedCornerShape(LocalDimens.current.roundedShapeRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LocalDimens.current.titleTextviewHeight)
            .background(Color.Black, roundedShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = QOrange)) { append("Q") }
                withStyle(SpanStyle(color = Color.White)) { append("deQ") }
                if (titleSuffix.isNotEmpty()) {
                    withStyle(SpanStyle(color = Color.White)) { append(titleSuffix) }
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = LocalDimens.current.textviewPaddingStart)
        )
        Box {
            IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier.size(LocalDimens.current.kebabbuttonSize)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_more_vert_white_24dp),
                    contentDescription = "More",
                    tint = Color.White
                )
            }
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.more_menu_settings)) },
                    onClick = { showMoreMenu = false; onOpenSettings() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.more_menu_privacy)) },
                    onClick = { showMoreMenu = false; onOpenPrivacy() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.more_menu_imprint)) },
                    onClick = { showMoreMenu = false; onOpenImprint() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.more_menu_licenses)) },
                    onClick = { showMoreMenu = false; onOpenLicenses() }
                )
            }
        }
    }
}
