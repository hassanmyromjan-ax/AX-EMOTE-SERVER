package com.axemote.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.UUID

/**
 * Holds the ONE WebView of this device/user. It is created inside the app process,
 * so cookies / localStorage / session are private to this install (multi-user safe).
 */
object WebViewStore {
    const val WEBSITE = "https://ax-emote-ultra.onrender.com/"
    private const val HOST = "ax-emote-ultra.onrender.com"
    private const val REFRESH_SCHEME = "axrefresh"

    var webView: WebView? = null

    var overlayShowing: Boolean = false

    var showingError: Boolean = false
        private set

    /** Set by MainActivity: called when the overlay page opens/closes. */
    var onOverlayChanged: (() -> Unit)? = null

    /** Set by MainActivity: page load progress 0..100. */
    var onProgress: ((Int) -> Unit)? = null

    /** Always-updated load progress (0..100) - used by the startup title screen. */
    @Volatile
    var progress: Int = 0

    private var app: Application? = null
    private val ui = Handler(Looper.getMainLooper())
    private var clearHistoryPending = false
    private var errorHtml: String? = null
    private var loadingHtml: String? = null

    // Refresh button: animated "Loading..." page + 5 second internet check
    private var checking = false
    private var checkStart = 0L

    fun detach(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    fun notifyOverlayChanged() {
        onOverlayChanged?.invoke()
    }

    fun init(application: Application) {
        if (webView != null) return
        app = application
        val web = WebView(application)
        webView = web
        configure(web, application)
        watchNetwork(application)
        loadSite()
    }

    /** Loads the website, or shows the Internet Error page immediately when offline. */
    fun loadSite() {
        val web = webView ?: return
        if (!isOnline()) {
            showError()
            return
        }
        showingError = false
        clearHistoryPending = true
        progress = 0
        val ctx = app
        if (ctx != null) {
            web.loadUrl(WEBSITE, mapOf("X-AX-Client-Id" to clientId(ctx)))
        } else {
            web.loadUrl(WEBSITE)
        }
    }

    /**
     * Refresh button of the error page:
     * shows the Loading page and checks the internet for 5 seconds.
     * - internet comes on  -> loads the website (Loading page stays until the website appears)
     * - still offline after 5 seconds -> the same Internet Error page comes back
     */
    fun refresh() {
        if (checking) return
        checking = true
        checkStart = SystemClock.uptimeMillis()
        showLoadingPage()
        ui.postDelayed(checkTick, 300)
    }

    private val checkTick = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.uptimeMillis() - checkStart
            if (isOnline() && elapsed >= 700) { // keep the Loading page visible for a moment
                checking = false
                loadSite()
                return
            }
            if (elapsed >= 5000) {
                checking = false
                showError()
                return
            }
            ui.postDelayed(this, 300)
        }
    }

    private fun showLoadingPage() {
        val web = webView ?: return
        showingError = true // treat like the error page (no history clearing / no back navigation)
        web.stopLoading()
        web.loadDataWithBaseURL(null, loadingPage(), "text/html", "UTF-8", null)
    }

    fun isOnline(): Boolean {
        val ctx = app ?: return true
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun showError() {
        val web = webView ?: return
        showingError = true
        web.stopLoading()
        web.loadDataWithBaseURL(null, errorPage(), "text/html", "UTF-8", null)
    }

    private fun loadingPage(): String {
        loadingHtml?.let { return it }
        val html = try {
            app!!.resources.openRawResource(R.raw.ax_loading)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            "<html><body style='background:#000;color:#fff;text-align:center;font-family:sans-serif'>" +
                "<h2 style='margin-top:40vh'>Loading...</h2></body></html>"
        }
        loadingHtml = html
        return html
    }

    private fun errorPage(): String {
        errorHtml?.let { return it }
        val html = try {
            app!!.resources.openRawResource(R.raw.ax_error)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            "<html><body style='background:#000;color:#fff;text-align:center;font-family:sans-serif'>" +
                "<h2 style='margin-top:30vh'>Your Internet Connection Error</h2>" +
                "<p>Please On Your Internet</p>" +
                "<p><a href='axrefresh://retry' style='color:#ff28d7'>REFRESH</a></p></body></html>"
        }
        errorHtml = html
        return html
    }

    private fun watchNetwork(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    // small delay so Wi-Fi <-> mobile data switching does not trigger a false error
                    ui.postDelayed({
                        if (!showingError && !checking && !isOnline()) showError()
                    }, 1200)
                }
            })
        } catch (_: Exception) {
        }
    }

    private fun clientId(ctx: Context): String {
        val sp = ctx.getSharedPreferences("ax_client", Context.MODE_PRIVATE)
        var id = sp.getString("id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            sp.edit().putString("id", id).apply()
        }
        return id
    }

    private fun handleUrl(uri: Uri): Boolean {
        val scheme = uri.scheme
        if (scheme == REFRESH_SCHEME) {
            ui.post { refresh() }
            return true
        }
        if (scheme == "about" || scheme == "data") return false
        if (scheme == "https" && uri.host == HOST) return false
        try {
            val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app?.startActivity(i)
        } catch (_: Exception) {
        }
        return true
    }

    private fun configure(web: WebView, ctx: Context) {
        val id = clientId(ctx)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadsImagesAutomatically = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(false)
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.textZoom = 100
        // unique per-install id so the website/backend can tell every user apart
        s.userAgentString = s.userAgentString + " AXApp/1.1 AXClient/" + id

        web.setBackgroundColor(Color.BLACK)
        web.overScrollMode = View.OVER_SCROLL_NEVER

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)
        cm.setCookie(WEBSITE, "ax_client_id=$id; Path=/; Max-Age=31536000; Secure; SameSite=Lax")
        cm.flush()

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
                onProgress?.invoke(newProgress)
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = handleUrl(request.url)

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return true
                return handleUrl(Uri.parse(url))
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (!request.isForMainFrame || showingError) return
                val desc = error.description?.toString() ?: ""
                if (desc.contains("ERR_ABORTED")) return
                ui.post { showError() }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!showingError && clearHistoryPending) {
                    clearHistoryPending = false
                    view?.clearHistory()
                }
            }
        }
    }
}
