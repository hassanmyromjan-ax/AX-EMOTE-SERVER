package com.axemote.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var toggle: TextView
    private val website = "https://ax-emote-ultra.onrender.com/"
    private val requestOverlay = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 8, 20)
        window.navigationBarColor = Color.rgb(7, 8, 20)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 8, 20))
        }

        val socials = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(7))
        }
        addSocial(socials, "TELEGRAM", "https://t.me/axemoteserver", 0xFF168DE2.toInt())
        addSocial(socials, "YOUTUBE", "https://www.youtube.com/@axromjan", 0xFFE62117.toInt())
        addSocial(socials, "TELEGRAM", "https://t.me/axromjanyt", 0xFF168DE2.toInt())
        root.addView(socials, LinearLayout.LayoutParams(-1, dp(58)))

        toggle = TextView(this).apply {
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(13), dp(18), dp(13))
            elevation = dp(5).toFloat()
            setOnClickListener { toggleOverlay() }
        }
        val toggleWrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(2), dp(12), dp(10))
            addView(toggle, LinearLayout.LayoutParams(-1, dp(50)))
        }
        root.addView(toggleWrap, LinearLayout.LayoutParams(-1, -2))

        val web = WebViewStore.webView ?: WebView(this).also {
            WebViewStore.webView = it
            configureWebView(it)
            if (isOnline()) it.loadUrl(website) else showOfflinePage(it)
        }
        WebViewStore.detach(web)
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateToggle()

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4103)
        }
    }

    private fun addSocial(row: LinearLayout, label: String, url: String, color: Int) {
        val button = TextView(this).apply {
            text = label
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(color, dp(15))
            elevation = dp(3).toFloat()
            setOnClickListener {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (_: Exception) { Toast.makeText(this@MainActivity, "App not available", Toast.LENGTH_SHORT).show() }
            }
        }
        val lp = LinearLayout.LayoutParams(0, dp(40), 1f)
        lp.setMargins(dp(4), 0, dp(4), 0)
        row.addView(button, lp)
    }

    private fun configureWebView(web: WebView) {
        web.setBackgroundColor(Color.BLACK)
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "axrefresh") {
                    if (isOnline()) view.loadUrl(website) else showOfflinePage(view)
                    return true
                }
                return if (uri.scheme == "https" && uri.host == "ax-emote-ultra.onrender.com") false else {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
                    true
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && !isOnline()) showOfflinePage(view)
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (!isOnline()) showOfflinePage(view)
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= 23) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun showOfflinePage(web: WebView) {
        val html = """
            <!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>
            <style>*{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(circle at top,#25204b,#080914 65%);color:#fff;font-family:Arial,sans-serif;display:flex;align-items:center;justify-content:center;text-align:center;padding:24px}.card{width:100%;max-width:440px;padding:34px 22px;border:1px solid #343052;border-radius:24px;background:#111222;box-shadow:0 18px 55px #0008}.wifi{font-size:64px;margin-bottom:18px}.title{font-size:26px;font-weight:800;line-height:1.25;color:#ff477e}.desc{font-size:16px;line-height:1.6;color:#d7d6e5;margin:16px 0 25px}button{border:0;border-radius:14px;padding:15px 35px;background:linear-gradient(90deg,#e60058,#a90086);color:white;font-size:16px;font-weight:800;letter-spacing:1px}</style></head>
            <body><div class='card'><div class='wifi'>🛜</div><div class='title'>Your Internet Connection Error<br>Please On Your Internet 🛜</div><div class='desc'>Your device appears to be offline. Turn on your internet connection, then tap Refresh.</div><button onclick=\"location.href='axrefresh://retry'\">↻ &nbsp; REFRESH</button></div></body></html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://ax-emote-ultra.onrender.com/", html, "text/html", "UTF-8", null)
    }

    private fun toggleOverlay() {
        if (OverlayPrefs.isOn(this)) {
            stopService(Intent(this, OverlayService::class.java))
            OverlayPrefs.setOn(this, false)
            Handler(Looper.getMainLooper()).postDelayed({
                val current = WebViewStore.webView
                if (current != null && current.parent == null && ::root.isInitialized) {
                    root.addView(current, LinearLayout.LayoutParams(-1, 0, 1f))
                }
                updateToggle()
            }, 180)
            updateToggle()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), requestOverlay)
            return
        }
        OverlayPrefs.setOn(this, true)
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        updateToggle()
    }

    @Deprecated("Deprecated by Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestOverlay) {
            if (Settings.canDrawOverlays(this)) toggleOverlay()
            else Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val web = WebViewStore.webView
        if (::root.isInitialized && web != null && web.parent == null && !WebViewStore.overlayShowing) {
            root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        if (::toggle.isInitialized) updateToggle()
    }

    @Deprecated("Deprecated by Android")
    override fun onBackPressed() {
        val web = WebViewStore.webView
        if (web != null && web.canGoBack() && !WebViewStore.overlayShowing) web.goBack() else super.onBackPressed()
    }

    private fun updateToggle() {
        if (!::toggle.isInitialized) return
        val on = OverlayPrefs.isOn(this)
        toggle.text = if (on) "OVERLAY PAGE ON" else "OVERLAY PAGE OFF"
        toggle.background = rounded(if (on) 0xFF159B62.toInt() else 0xFFBE145C.toInt(), dp(15))
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat(); setStroke(dp(1), 0x55FFFFFF)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
