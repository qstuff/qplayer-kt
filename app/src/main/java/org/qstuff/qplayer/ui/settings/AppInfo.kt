package org.qstuff.qplayer.ui.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import org.qstuff.qplayer.BuildConfig

/** "qdeq 3.0.0 (532)", or "qdeq-dev …" in debug builds — shown in Settings. */
fun Context.appVersionString(): String {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val prefix = if (BuildConfig.DEBUG) "qdeq-dev" else "qdeq"
    return "$prefix ${packageInfo.versionName} (${packageInfo.versionCodeCompat()})"
}

/** Manufacturer, model and CPU ABI — shown in Settings. */
@Suppress("DEPRECATION")
fun deviceInfoString() = "${Build.MANUFACTURER} ${Build.MODEL} ${Build.CPU_ABI}"

@Suppress("DEPRECATION")
private fun PackageInfo.versionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
