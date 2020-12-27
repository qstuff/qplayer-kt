package org.qstuff.qplayer.settings

import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import timber.log.Timber

class SettingsFragment : PreferenceFragmentCompat(), KoinComponent,
        SharedPreferences.OnSharedPreferenceChangeListener{

    private val preferencesDataSource by inject<PreferencesDataSource>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceScreen.preferenceManager.sharedPreferencesName = "QDEQ"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        (findPreference<SwitchPreference>(getString(R.string.prefs_key_track_autostart)))!!.isChecked =
                preferencesDataSource.isAutostartEnabled()

        (findPreference<SwitchPreference>(getString(R.string.prefs_key_proceed_to_next_track)))!!.isChecked =
                preferencesDataSource.isProceedToNextTrackEnabled()

        (findPreference<SwitchPreference>(getString(R.string.prefs_key_skip_back_to_start)))!!.isChecked =
                preferencesDataSource.isSkipBackToStartEnabled()

        (findPreference<SwitchPreference>(getString(R.string.prefs_key_show_clear_queue_dialog)))!!.isChecked =
                preferencesDataSource.isShowClearQueueWarningEnabled()

        (findPreference<SwitchPreference>(getString(R.string.prefs_key_start_foreground)))!!.isChecked =
                preferencesDataSource.isStartForegroundEnabled()

        (findPreference<SwitchPreference>(getString(R.string.prefs_key_stop_playback_on_cue)))!!.isChecked =
                preferencesDataSource.isStopPlaybackOnSettingCuepointEnabled()

        (findPreference<ListPreference>(getString(R.string.prefs_key_jogwheel_sensitivity)))!!.value =
                preferencesDataSource.getJogWheelSensitivity().toString()

        (findPreference<SwitchPreference>(getString(R.string.prefs_key_enable_crashreporting)))!!.isChecked =
                preferencesDataSource.isCrashreportingEnabled()

        findPreference<Preference>(getString(R.string.prefs_key_app_version))!!.summary = getVersionString()

        findPreference<Preference>(getString(R.string.prefs_key_device_info))!!.summary = getDeviceInfoString()
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        Timber.d("onPreferenceTreeClick(): ${preference?.key}")
        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Timber.d("onSharedPreferenceChanged(): key: $key")
    }

    private fun getVersionString(): String {
        val packageInfo = activity?.packageManager?.getPackageInfo(activity?.packageName, 0)

        packageInfo ?: return "n/a"

        return if (BuildConfig.DEBUG) {
            ("qdeq-α ${packageInfo.versionName} (${getVersionCode(packageInfo)})")
        } else {
            ("qdeq ${packageInfo.versionName} (${getVersionCode(packageInfo)}")
        }
    }

    private fun getDeviceInfoString(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} ${Build.CPU_ABI}"
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode(packageInfo: PackageInfo) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
}