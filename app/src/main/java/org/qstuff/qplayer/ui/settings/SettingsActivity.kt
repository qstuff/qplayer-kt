package org.qstuff.qplayer.ui.settings

import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.ui.player.jogwheel.JogwheelHaptics
import org.qstuff.qplayer.ui.lockOrientationForDevice
import org.qstuff.qplayer.ui.theme.QDeqTheme

class SettingsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockOrientationForDevice(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)

        val settingsViewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
        val appVersion = getVersionString()
        val deviceInfo = getDeviceInfoString()
        val canVibrate = JogwheelHaptics.isSupported(this)

        setContent {
            QDeqTheme {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    appVersion = appVersion,
                    deviceInfo = deviceInfo,
                    canVibrate = canVibrate,
                    onBack = ::finish
                )
            }
        }
    }

    private fun getVersionString(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)

        return if (BuildConfig.DEBUG) {
            "qdeq-dev ${packageInfo.versionName} (${getVersionCode(packageInfo)})"
        } else {
            "qdeq ${packageInfo.versionName} (${getVersionCode(packageInfo)})"
        }
    }

    @Suppress("DEPRECATION")
    private fun getDeviceInfoString() = "${Build.MANUFACTURER} ${Build.MODEL} ${Build.CPU_ABI}"

    @Suppress("DEPRECATION")
    private fun getVersionCode(packageInfo: PackageInfo) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
}
