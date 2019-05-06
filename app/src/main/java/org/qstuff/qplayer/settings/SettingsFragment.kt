package org.qstuff.qplayer.settings

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import kotlinx.android.synthetic.main.activity_player.*
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.BuildConfig
import org.qstuff.qplayer.QDeqApplication
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

        (findPreference(getString(R.string.prefs_key_track_autostart)) as SwitchPreference).isChecked =
                preferencesDataSource.isAutostartEnabled()

        (findPreference(getString(R.string.prefs_key_autostart_next)) as SwitchPreference).isChecked =
                preferencesDataSource.isAutoPlayNextTrackEnabled()

        (findPreference(getString(R.string.prefs_key_skip_back_to_start)) as SwitchPreference).isChecked =
                preferencesDataSource.isSkipBackToStartEnabled()

        (findPreference(getString(R.string.prefs_key_show_clear_queue_dialog)) as SwitchPreference).isChecked =
                preferencesDataSource.isShowClearQueueWarningEnabled()

        (findPreference(getString(R.string.prefs_key_stop_playback_on_cue)) as SwitchPreference).isChecked =
                preferencesDataSource.isStopPlaybackOnSettingCuepointEnabled()

        (findPreference(getString(R.string.prefs_key_enable_remain_blink)) as SwitchPreference).isChecked =
                preferencesDataSource.isBlinkingRemainEnabled()

        (findPreference(getString(R.string.prefs_key_enable_crashreporting)) as SwitchPreference).isChecked =
                preferencesDataSource.isCrashreportingEnabled()

        findPreference(getString(R.string.prefs_key_app_version)).summary = getVersionString()
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

        val value = sharedPreferences?.getBoolean(key, false)

        Timber.d("onSharedPreferenceChanged(): val: $value")

    }

    private fun getVersionString(): String {

        var debugTitleSuffix =""
        val packageInfo = activity?.packageManager?.getPackageInfo(activity?.packageName, 0)

        if (BuildConfig.DEBUG) {
            debugTitleSuffix = ("qdeq-α ${packageInfo?.versionName} (${packageInfo?.versionCode})")
        } else {
            debugTitleSuffix = ("qdeq ${packageInfo?.versionName} (${packageInfo?.versionCode})")
        }
        return debugTitleSuffix
    }
}