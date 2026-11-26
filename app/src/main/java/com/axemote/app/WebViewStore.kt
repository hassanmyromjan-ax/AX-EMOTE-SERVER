package com.axemote.app

import android.animation.ValueAnimator
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
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

    /** What the hosts (main screen / overlay card) actually attach: WebView + Loading cover. */
    var container: FrameLayout? = null

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
        container = FrameLayout(application).apply {
            setBackgroundColor(Color.BLACK)
            addView(web, FrameLayout.LayoutParams(-1, -1))
        }
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
     * shows the Loading cover and checks the internet for 5 seconds.
     * - internet comes on  -> loads the website (Loading cover stays until the website is fully loaded)
     * - still offline after 5 seconds -> the same Internet Error page comes back
     */
    fun refresh() {
        if (checking) return
        checking = true
        checkStart = SystemClock.uptimeMillis()
        showLoadingOverlay()
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

    private var loadingView: LoadingView? = null
    private val loadingTimeout = Runnable { hideLoadingOverlay(true) }

    /**
     * Native "Loading..." cover drawn ON TOP of the WebView. It stays until the website
     * has really finished loading, so the half-loaded page is never visible.
     */
    private fun showLoadingOverlay() {
        val box = container ?: return
        ui.removeCallbacks(loadingTimeout)
        var v = loadingView
        if (v == null) {
            v = LoadingView(box.context)
            loadingView = v
            box.addView(v, FrameLayout.LayoutParams(-1, -1))
        }
        v.animate().cancel()
        v.alpha = 1f
        ui.postDelayed(loadingTimeout, 60000) // safety
    }

    private fun hideLoadingOverlay(animated: Boolean) {
        val v = loadingView ?: return
        ui.removeCallbacks(loadingTimeout)
        loadingView = null
        val box = container
        if (animated) {
            v.animate().alpha(0f).setStartDelay(150).setDuration(250).withEndAction { box?.removeView(v) }.start()
        } else {
            v.animate().cancel()
            box?.removeView(v)
        }
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
        hideLoadingOverlay(false)
        web.stopLoading()
        web.loadDataWithBaseURL(null, errorPage(), "text/html", "UTF-8", null)
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
                // website really finished -> remove the Loading cover
                if (!showingError && url != null && url.contains(HOST)) hideLoadingOverlay(true)
            }
        }
    }
}


/** Animated "Loading..." screen (ring + wifi icon + text), drawn natively with Canvas. */
class LoadingView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private var phase = 0f
    private var anim: ValueAnimator? = null
    private val bgP = Paint()
    private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#2A1648") }
    private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#FF2BD6")
    }
    private val arcP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#FFE24D")
    }
    private val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#FFE24D") }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    private val rect = RectF()

    init { isClickable = true } // swallow touches while loading

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bgP.shader = RadialGradient(
            w / 2f, 0f, h * 0.9f,
            intArrayOf(Color.parseColor("#33104A"), Color.parseColor("#080716"), Color.BLACK),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgP)

        val s = (minOf(w, h) * 0.28f).coerceIn(84f * d, 130f * d)
        val cx = w / 2f
        val cy = h / 2f - s * 0.35f
        val r = s * 0.45f

        // spinning ring
        trackP.strokeWidth = s * 0.05f
        ringP.strokeWidth = s * 0.05f
        canvas.drawCircle(cx, cy, r, trackP)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, phase * 360f, 100f, false, ringP)

        // wifi icon: arcs light up one after another
        arcP.strokeWidth = s * 0.055f
        val ay = cy + s * 0.20f
        val lit = (phase * 4f).toInt() % 4
        for (k in 0..2) {
            val rr = s * (0.10f + 0.11f * k)
            rect.set(cx - rr, ay - rr, cx + rr, ay + rr)
            arcP.alpha = if (k < lit) 255 else 70
            canvas.drawArc(rect, 225f, 90f, false, arcP)
        }
        canvas.drawCircle(cx, ay, s * 0.035f, dotP)

        // "Loading..." text under the icon
        textP.textSize = s * 0.24f
        val word = "Loading"
        val wordW = textP.measureText(word)
        val dotW = textP.measureText(".")
        val x0 = cx - (wordW + dotW * 3f) / 2f
        val y = cy + r + s * 0.42f
        textP.alpha = 255
        canvas.drawText(word, x0, y, textP)
        val twoPi = (2.0 * Math.PI)
        for (i in 0..2) {
            val a = 0.5 + 0.5 * Math.sin(twoPi * (phase - i * 0.2))
            textP.alpha = (60 + 195 * a).toInt().coerceIn(0, 255)
            canvas.drawText(".", x0 + wordW + dotW * i, y, textP)
        }
        textP.alpha = 255
    }
}
