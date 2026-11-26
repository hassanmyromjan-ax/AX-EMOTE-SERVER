package com.axemote.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webHolder: FrameLayout
    private lateinit var toggle: TextView
    private lateinit var placeholder: TextView
    private val requestOverlay = 4102

    // theme (matches the black / green website)
    private val bg = Color.parseColor("#101113")
    private val green = Color.parseColor("#00E676")
    private val blood = Color.parseColor("#E01B3C")

    private val ui = Handler(Looper.getMainLooper())
    private var pulse: ObjectAnimator? = null

    // startup title screen
    private var splash: FrameLayout? = null
    private lateinit var splashBar: DualProgressBar
    private lateinit var splashPct: TextView
    private val splashAnims = ArrayList<Pair<View, Long>>()
    private var fx: SplashFxView? = null
    private var splashCredit: View? = null
    private var shown = 0f
    private var fake = 0f
    private var splashStart = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        // normally already created by AxApp (fast start); this is only a safety net
        WebViewStore.init(application)

        val frame = FrameLayout(this)
        frame.addView(buildUi(), FrameLayout.LayoutParams(-1, -1))
        val sp = buildSplash()
        splash = sp
        frame.addView(sp, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)

        attachWeb()
        updateToggle()

        WebViewStore.onOverlayChanged = { attachWeb(); updateToggle() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4103)
        }

        startSplash()
    }

    // ------------------------------------------------------------------ main UI

    private fun buildUi(): View {
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

        val socials = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        socials.addView(
            social("✈", Color.parseColor("#2AABEE"), "TELEGRAM", "https://t.me/axemoteserver"),
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) }
        )
        socials.addView(
            social("▶", Color.parseColor("#FF3B3B"), "YOUTUBE", "https://www.youtube.com/@axromjan"),
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) }
        )
        socials.addView(
            social("✈", Color.parseColor("#2AABEE"), "TELEGRAM", "https://t.me/axromjanyt"),
            LinearLayout.LayoutParams(0, dp(46), 1f)
        )
        header.addView(socials, LinearLayout.LayoutParams(-1, -2))

        toggle = TextView(this).apply {
            textSize = 17f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            setOnClickListener { toggleOverlay() }
        }
        header.addView(toggle, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(10) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        // thin divider between header and website
        val line = View(this).apply { setBackgroundColor(Color.argb(70, 0, 230, 118)) }
        root.addView(line, LinearLayout.LayoutParams(-1, dp(1)))

        webHolder = FrameLayout(this)
        root.addView(webHolder, LinearLayout.LayoutParams(-1, 0, 1f))

        placeholder = TextView(this).apply {
            text = "OVERLAY PAGE IS RUNNING\n\nOpen your game and tap the floating AX icon"
            setTextColor(Color.rgb(120, 160, 135))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        webHolder.addView(placeholder, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    private fun social(glyph: String, glyphColor: Int, label: String, url: String): TextView {
        val ss = SpannableString("$glyph  $label")
        ss.setSpan(ForegroundColorSpan(glyphColor), 0, glyph.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return TextView(this).apply {
            text = ss
            textSize = 12.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.04f
            setTextColor(green)
            gravity = Gravity.CENTER
            maxLines = 1
            background = pill(
                Color.parseColor("#1D2022"), Color.parseColor("#121415"),
                14, Color.argb(210, 0, 230, 118)
            )
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Cannot open link", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pill(c1: Int, c2: Int, radiusDp: Int, strokeColor: Int): RippleDrawable {
        val r = dp(radiusDp).toFloat()
        val stroke = (resources.displayMetrics.density * 1.5f).toInt()
        val face = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c1, c2)).apply {
            cornerRadius = r
            setStroke(stroke, strokeColor)
        }
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = r }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(60, 0, 230, 118)), face, mask)
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
        val iconSize = dp(30)
        if (on) {
            // ON: electric aqua button, deep-navy text, lightning-bolt icon
            toggle.text = "OVERLAY PAGE ON"
            toggle.setTextColor(Color.parseColor("#00262E"))
            toggle.background = pill(
                Color.parseColor("#00F0D0"), Color.parseColor("#00A8C8"),
                18, Color.parseColor("#9CFFF0")
            )
            toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ToggleIconDrawable(true, Color.parseColor("#00F0D0"), Color.parseColor("#00323C"), iconSize),
                null, null, null
            )
        } else {
            // OFF: royal violet button, cream-gold text, power icon
            toggle.text = "OVERLAY PAGE OFF"
            toggle.setTextColor(Color.parseColor("#FFEFC0"))
            toggle.background = pill(
                Color.parseColor("#7B3FF2"), Color.parseColor("#3A1494"),
                18, Color.parseColor("#B79BFF")
            )
            toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ToggleIconDrawable(false, Color.parseColor("#FFE9A0"), Color.parseColor("#230A63"), iconSize),
                null, null, null
            )
        }
        toggle.compoundDrawablePadding = dp(10)
    }

    /** Light zoom in / zoom out of the ON-OFF button. */
    private fun startPulse() {
        if (!::toggle.isInitialized) return
        pulse?.cancel()
        toggle.scaleX = 1f; toggle.scaleY = 1f
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            toggle,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.035f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.035f)
        ).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // ------------------------------------------------------------------ startup title screen

    private fun buildSplash(): FrameLayout {
        val sp = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true // blocks touches while it is visible
        }
        val fxView = SplashFxView(this)
        fx = fxView
        sp.addView(fxView, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        sp.addView(col, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))

        val dm = resources.displayMetrics
        val base = minOf(dm.widthPixels * 0.15f, dm.heightPixels * 0.11f)
        val tight = -(base * 0.18f).toInt()

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            rotation = -4f
        }
        title.addView(axRow(base * 1.35f), LinearLayout.LayoutParams(-2, -2))
        title.addView(wordRow("EMOTE", base * 1.12f, 'L', 150L), LinearLayout.LayoutParams(-2, -2).apply { topMargin = tight })
        title.addView(wordRow("SERVER", base, 'B', 400L), LinearLayout.LayoutParams(-2, -2).apply { topMargin = tight })
        col.addView(title, LinearLayout.LayoutParams(-2, -2))

        val barW = (dm.widthPixels * 0.66f).toInt()
        splashBar = DualProgressBar(this)
        col.addView(splashBar, LinearLayout.LayoutParams(barW, dp(6)).apply { topMargin = dp(46) })

        splashPct = TextView(this).apply {
            text = "0%"
            setTextColor(Color.parseColor("#FFE24D"))
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 15f
            letterSpacing = 0.1f
        }
        col.addView(splashPct, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })

        // small credit at the very bottom: line - text - line (same width as the text)
        val lineColor = Color.argb(200, 255, 43, 214)
        val credit = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0f
        }
        credit.addView(View(this).apply { setBackgroundColor(lineColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        credit.addView(
            TextView(this).apply {
                text = "AX ROMJAN YT"
                setTextColor(Color.parseColor("#FFE082"))
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 11f
                letterSpacing = 0.3f
            },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(5); bottomMargin = dp(5) }
        )
        credit.addView(View(this).apply { setBackgroundColor(lineColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        sp.addView(credit, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(40) })
        splashCredit = credit
        return sp
    }

    /** "AX" arrives from the RIGHT, with the shield and the game controller popping in beside it. */
    private fun axRow(sizePx: Float): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val iw = (sizePx * 0.42f).toInt()
        val shield = MiniIconView(this, MiniIconView.SHIELD)
        row.addView(shield, LinearLayout.LayoutParams(iw, (iw * 1.1f).toInt()).apply { marginEnd = dp(4) })
        prepareIcon(shield, 380L)
        var i = 0L
        for (ch in "AX") {
            val v = TitleLetterView(this, ch.toString(), sizePx)
            prepareFly(v, 'R', i * 70L)
            row.addView(v)
            i++
        }
        val pad = MiniIconView(this, MiniIconView.PAD)
        row.addView(pad, LinearLayout.LayoutParams((iw * 1.3f).toInt(), (iw * 0.95f).toInt()).apply { marginStart = dp(4) })
        prepareIcon(pad, 460L)
        return row
    }

    /** EMOTE arrives from the LEFT, SERVER from the BOTTOM (letter by letter). */
    private fun wordRow(word: String, sizePx: Float, from: Char, startDelay: Long): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        var i = 0L
        for (ch in word) {
            val v = TitleLetterView(this, ch.toString(), sizePx)
            prepareFly(v, from, startDelay + i * 70L)
            row.addView(v)
            i++
        }
        return row
    }

    private fun prepareFly(v: View, from: Char, delay: Long) {
        val dm = resources.displayMetrics
        v.alpha = 0f
        when (from) {
            'L' -> v.translationX = -dm.widthPixels.toFloat()
            'R' -> v.translationX = dm.widthPixels.toFloat()
            'T' -> v.translationY = -dm.heightPixels.toFloat()
            else -> v.translationY = dm.heightPixels.toFloat()
        }
        splashAnims.add(Pair(v, delay))
    }

    private fun prepareIcon(v: View, delay: Long) {
        v.alpha = 0f
        v.scaleX = 0f
        v.scaleY = 0f
        splashAnims.add(Pair(v, delay))
    }

    private fun startSplash() {
        splashStart = System.currentTimeMillis()
        fx?.start()
        splashAnims.forEach { (v, delay) ->
            v.animate()
                .translationX(0f).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(delay)
                .setDuration(600)
                .setInterpolator(OvershootInterpolator(0.9f))
                .start()
        }
        splashCredit?.animate()?.alpha(1f)?.setStartDelay(700)?.setDuration(500)?.start()
        ui.post(tick)
    }

    /** Percent follows the real WebView load; at 100% the splash fades out. */
    private val tick = object : Runnable {
        override fun run() {
            val real = WebViewStore.progress
            val timedOut = System.currentTimeMillis() - splashStart > 40000
            val elapsed = System.currentTimeMillis() - splashStart
            val done = (real >= 100 && elapsed >= 1500) || timedOut
            if (!done) fake += (90f - fake) * 0.012f
            val target = if (done) 100f else minOf(95f, maxOf(real.toFloat(), fake))
            val diff = target - shown
            if (diff > 0f) shown += (diff * 0.08f).coerceIn(0.3f, 1.2f).coerceAtMost(diff)
            splashBar.value = shown
            splashPct.text = "${shown.toInt()}%"
            if (shown >= 99.9f) {
                splashBar.value = 100f
                splashPct.text = "100%"
                ui.postDelayed({ hideSplash() }, 300)
                return
            }
            ui.postDelayed(this, 16)
        }
    }

    private fun hideSplash() {
        val sp = splash ?: return
        sp.animate().alpha(0f).setDuration(300).withEndAction {
            sp.visibility = View.GONE
            fx?.stop()
            splash = null
        }.start()
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
        startPulse()
    }

    override fun onPause() {
        pulse?.cancel()
        pulse = null
        super.onPause()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        fx?.stop()
        pulse?.cancel()
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
