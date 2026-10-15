package org.qstuff.qplayer.ui

import android.app.Activity
import android.content.pm.ActivityInfo

/** Tablets (smallest width ≥ 600dp) use the landscape two-pane player layout. */
fun Activity.isTablet() = resources.configuration.smallestScreenWidthDp >= 600

/**
 * Locks the orientation per device class: landscape (either way round) on tablets,
 * [phoneOrientation] on phones. Set in code because the manifest can't differ per screen size.
 */
fun Activity.lockOrientationForDevice(phoneOrientation: Int) {
    requestedOrientation = if (isTablet()) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        phoneOrientation
    }
}
