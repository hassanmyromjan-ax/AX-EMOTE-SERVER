
package com.axemote.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var toggle: TextView
    private val website = "https://ax-emote-ultra.onrender.com/"
    private val requestOverlay = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(5, 8, 35)
        window.navigationBarColor = Color.rgb(5, 8, 35)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.rgb(5, 8, 35))

        var web = WebViewStore.webView
        if (web == null) {
            web = WebView(this)
            WebViewStore.webView = web
            configureWebView(web)
            web.loadUrl(website)
        }

        WebViewStore.detach(web)
        root.addView(web, FrameLayout.LayoutParams(-1, -1))

        toggle = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(18, 12, 18, 12)
            setBackgroundColor(Color.rgb(20, 15, 70))
            elevation = 10f
            setOnClickListener { toggleOverlay() }
        }

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        lp.bottomMargin = 18
        root.addView(toggle, lp)

        setContentView(root)
        updateToggle()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4103
            )
        }
    }

    private fun configureWebView(web: WebView) {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        web.settings.setSupportMultipleWindows(false)
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                return if (
                    uri.scheme == "https" &&
                    uri.host == "ax-emote-ultra.onrender.com"
                ) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {}
                    true
                }
            }
        }
    }

    private fun toggleOverlay() {
        if (OverlayPrefs.isOn(this)) {
            stopService(Intent(this, OverlayService::class.java))
            OverlayPrefs.setOn(this, false)
            Handler(Looper.getMainLooper()).postDelayed({
                val web = WebViewStore.webView
                if (web != null && web.parent == null) {
                    root.addView(web, 0, FrameLayout.LayoutParams(-1, -1))
                }
                updateToggle()
            }, 180)
            updateToggle()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ),
                requestOverlay
            )
            return
        }

        OverlayPrefs.setOn(this, true)
        val intent = Intent(this, OverlayService::class.java)

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        updateToggle()
    }

    @Deprecated("Deprecated by Android")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == requestOverlay) {
            if (Settings.canDrawOverlays(this)) {
                toggleOverlay()
            } else {
                Toast.makeText(
                    this,
                    "Overlay permission is required",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val web = WebViewStore.webView
        if (::root.isInitialized && web != null &&
            web.parent == null && !WebViewStore.overlayShowing
        ) {
            root.addView(web, 0, FrameLayout.LayoutParams(-1, -1))
        }

        if (::toggle.isInitialized) updateToggle()
    }

    @Deprecated("Deprecated by Android")
    override fun onBackPressed() {
        val web = WebViewStore.webView
        if (web != null && web.canGoBack() &&
            !WebViewStore.overlayShowing
        ) {
            web.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun updateToggle() {
        if (!::toggle.isInitialized) return

        val on = OverlayPrefs.isOn(this)
        toggle.text = if (on) {
            "AX OVERLAY • ON | TAP TO TURN OFF"
        } else {
            "AX OVERLAY • OFF | TAP TO TURN ON"
        }
    }
}
