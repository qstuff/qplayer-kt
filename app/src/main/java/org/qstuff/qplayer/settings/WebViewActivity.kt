package org.qstuff.qplayer.settings

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.webview.*
import org.qstuff.qplayer.R

class WebViewActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "EXTRA_URL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)

        setContentView(R.layout.webview)


        if (url != null) {
            webView.loadUrl("file:///android_asset/$url")
        }
    }
}