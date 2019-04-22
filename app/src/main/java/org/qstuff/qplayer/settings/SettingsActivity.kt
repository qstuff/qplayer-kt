package org.qstuff.qplayer.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.qstuff.qplayer.R

class SettingsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
    }
}