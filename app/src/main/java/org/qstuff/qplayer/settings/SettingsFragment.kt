package org.qstuff.qplayer.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import org.qstuff.qplayer.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

    }
}