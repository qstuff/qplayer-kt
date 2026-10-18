package org.qstuff.qplayer.util

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import org.qstuff.qplayer.BuildConfig

/** Android [PlatformInfo]: version from the package manager, device from Build. */
class AndroidPlatformInfo(context: Context) : PlatformInfo {

    override val appVersion: String = run {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val prefix = if (BuildConfig.DEBUG) "QdeQ-dev" else "QdeQ"
        "$prefix ${packageInfo.versionName} (${packageInfo.versionCodeCompat()})"
    }

    @Suppress("DEPRECATION")
    override val deviceInfo: String = "${Build.MANUFACTURER} ${Build.MODEL} ${Build.CPU_ABI}"

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
}
