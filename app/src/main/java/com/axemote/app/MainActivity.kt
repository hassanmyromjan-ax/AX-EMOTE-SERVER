package com.axemote.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webHolder: FrameLayout
    private lateinit var toggle: TextView
    private lateinit var placeholder: TextView
    private lateinit var progress: ProgressBar
    private val requestOverlay = 4102
    private val bg = Color.rgb(5, 8, 35)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        // normally already created by AxApp (fast start); this is only a safety net
        WebViewStore.init(application)

        buildUi()
        attachWeb()
        updateToggle()

        WebViewStore.onOverlayChanged = { attachWeb(); updateToggle() }
        WebViewStore.onProgress = { p ->
            progress.progress = p
            progress.visibility = if (p in 1..99) View.VISIBLE else View.GONE
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4103)
        }
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        // 3 social buttons: left / middle / right
        val socials = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        socials.addView(
            social("✈  Telegram", "#2AABEE", "#1B6FB5", "https://t.me/axemoteserver"),
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) }
        )
        socials.addView(
            social("▶  YouTube", "#FF2A2A", "#B80000", "https://www.youtube.com/@axromjan"),
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) }
        )
        socials.addView(
            social("✈  Telegram", "#2AABEE", "#1B6FB5", "https://t.me/axromjanyt"),
            LinearLayout.LayoutParams(0, dp(46), 1f)
        )
        header.addView(socials, LinearLayout.LayoutParams(-1, -2))

        // big overlay ON / OFF button
        toggle = TextView(this).apply {
            textSize = 19f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.08f
            setTextColor(Color.WHITE)
            setShadowLayer(6f, 0f, 2f, Color.argb(120, 0, 0, 0))
            gravity = Gravity.CENTER
            setOnClickListener { toggleOverlay() }
        }
        header.addView(toggle, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(10) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        // website area
        webHolder = FrameLayout(this)
        root.addView(webHolder, LinearLayout.LayoutParams(-1, 0, 1f))

        placeholder = TextView(this).apply {
            text = "OVERLAY PAGE IS RUNNING\n\nOpen your game and tap the floating AX icon"
            setTextColor(Color.rgb(154, 160, 216))
            textSize = 14f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        webHolder.addView(placeholder, FrameLayout.LayoutParams(-1, -1))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(Color.rgb(255, 40, 215))
            progressBackgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
        webHolder.addView(progress, FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP))

        setContentView(root)
    }

    private fun social(label: String, c1: String, c2: String, url: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            background = pill(Color.parseColor(c1), Color.parseColor(c2), 14)
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Cannot open link", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pill(c1: Int, c2: Int, radiusDp: Int): RippleDrawable {
        val r = dp(radiusDp).toFloat()
        val face = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply { cornerRadius = r }
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = r }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(80, 255, 255, 255)), face, mask)
    }

    private fun attachWeb() {
        if (!::webHolder.isInitialized) return
        val web = WebViewStore.webView ?: return
        placeholder.visibility = if (WebViewStore.overlayShowing) View.VISIBLE else View.GONE
        if (WebViewStore.overlayShowing) return
        if (web.parent !== webHolder) {
            WebViewStore.detach(web)
            webHolder.addView(web, 0, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun updateToggle() {
        if (!::toggle.isInitialized) return
        val on = OverlayPrefs.isOn(this)
        toggle.text = if (on) "OVERLAY PAGE ON" else "OVERLAY PAGE OFF"
        toggle.background = if (on) {
            pill(Color.parseColor("#00C853"), Color.parseColor("#00B8D4"), 18)
        } else {
            pill(Color.parseColor("#E0103A"), Color.parseColor("#9C00C8"), 18)
        }
    }

    // ------------------------------------------------------------------ overlay logic (unchanged)

    private fun toggleOverlay() {
        if (OverlayPrefs.isOn(this)) {
            stopService(Intent(this, OverlayService::class.java))
            OverlayPrefs.setOn(this, false)
            Handler(Looper.getMainLooper()).postDelayed({
                attachWeb()
                updateToggle()
            }, 180)
            updateToggle()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                requestOverlay
            )
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
            if (Settings.canDrawOverlays(this)) {
                toggleOverlay()
            } else {
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        attachWeb()
        updateToggle()
    }

    override fun onDestroy() {
        WebViewStore.onOverlayChanged = null
        WebViewStore.onProgress = null
        val web = WebViewStore.webView
        if (web != null && web.parent === webHolder) webHolder.removeView(web)
        super.onDestroy()
    }

    @Deprecated("Deprecated by Android")
    override fun onBackPressed() {
        val web = WebViewStore.webView
        if (web != null && web.canGoBack() &&
            !WebViewStore.overlayShowing && !WebViewStore.showingError
        ) {
            web.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
