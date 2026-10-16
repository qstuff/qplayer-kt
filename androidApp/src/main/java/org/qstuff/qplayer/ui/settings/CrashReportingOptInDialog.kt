package org.qstuff.qplayer.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import org.qstuff.qplayer.R

/**
 * Asks once (first start) whether crash reports may be sent. It can't be dismissed without a
 * choice; the choice can be changed in Settings anytime.
 */
@Composable
fun CrashReportingOptInDialog(onChoice: (enabled: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.dialog_crashlytics_opt_in_title)) },
        text = { Text(stringResource(R.string.dialog_crashlytics_opt_in_message)) },
        confirmButton = {
            TextButton(onClick = { onChoice(true) }) {
                Text(stringResource(R.string.dialog_crashlytics_opt_in_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoice(false) }) {
                Text(stringResource(R.string.dialog_crashlytics_opt_in_no_thanks))
            }
        }
    )
}
