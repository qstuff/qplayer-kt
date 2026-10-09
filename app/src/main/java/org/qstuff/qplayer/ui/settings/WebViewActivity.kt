package org.qstuff.qplayer.ui.settings

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.qstuff.qplayer.databinding.WebviewBinding
import org.qstuff.qplayer.ui.lockOrientationForDevice


class WebViewActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "EXTRA_URL"
    }

    private lateinit var binding: WebviewBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockOrientationForDevice(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
        val url = intent.getStringExtra(EXTRA_URL)

        binding = WebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // The page canvas is white by default (the view's android:background doesn't apply to it).
        binding.webView.setBackgroundColor(Color.BLACK)


        if (url != null) {
            binding.webView.loadUrl("file:///android_asset/$url")
        }
    }
}