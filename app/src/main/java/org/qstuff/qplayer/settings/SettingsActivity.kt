package org.qstuff.qplayer.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.qstuff.qplayer.R
import org.qstuff.qplayer.util.replaceFragment

class SettingsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        replaceFragment(SettingsFragment(), R.id.settingsContainer )
    }
}