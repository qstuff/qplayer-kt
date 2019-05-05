package org.qstuff.qplayer.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.queue.QueueViewModel
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
}