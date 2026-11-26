package com.axemote.app

import android.app.Application

/**
 * Starts the WebView as early as possible (before any Activity is shown)
 * so the website is already loading while the app UI is being drawn.
 */
class AxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // A fresh process can never have a running overlay service.
        OverlayPrefs.setOn(this, false)
        try {
            WebViewStore.init(this)
        } catch (_: Exception) {
        }
    }
}
