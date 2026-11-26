package com.axemote.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var icon: ImageView? = null
    private var overlayRoot: FrameLayout? = null
    private var iconParams: WindowManager.LayoutParams? = null
    private var pageParams: WindowManager.LayoutParams? = null
    private val channelId = "ax_overlay_service"

    // remembered geometry
    private var lastIconX = -1
    private var lastIconY = -1
    private val savedBounds = arrayOfNulls<IntArray>(2) // 0 = portrait, 1 = landscape : x,y,w,h
    private var lastSW = 0
    private var lastSH = 0

    private val barColor = Color.rgb(8, 11, 43)

    // "drag icon to the bottom to remove it" target (portrait only)
    private var targetWin: FrameLayout? = null
    private var targetIcon: IconView? = null
    private var targetShown = false
    private var targetActive = false

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val s = screenSize()
        lastSW = s[0]; lastSH = s[1]
        createChannel()
        createRemoveTarget() // added BEFORE the icon so the icon is always drawn above it
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7001, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(7001, notification())
        }
        showFloatingIcon()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!OverlayPrefs.isOn(this)) { stopSelf(); return START_NOT_STICKY }
        if (icon == null && overlayRoot == null) showFloatingIcon()
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------ screen / rotation

    @Suppress("DEPRECATION")
    private fun screenSize(): IntArray {
        val dm = DisplayMetrics()
        val d = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        d.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onScreenChanged()
        handler.postDelayed({ onScreenChanged() }, 250)
    }

    /** Overlay follows the phone orientation automatically. */
    private fun onScreenChanged() {
        val s = screenSize()
        val sw = s[0]; val sh = s[1]
        if (sw == lastSW && sh == lastSH) return
        val oldLand = lastSW > lastSH
        val newLand = sw > sh
        val p = pageParams
        if (p != null && overlayRoot != null) {
            if (oldLand != newLand) {
                savedBounds[if (oldLand) 1 else 0] = intArrayOf(p.x, p.y, p.width, p.height)
                val saved = savedBounds[if (newLand) 1 else 0]
                applyBounds(clampBounds(saved ?: defaultBounds(sw, sh), sw, sh))
            } else {
                applyBounds(clampBounds(intArrayOf(p.x, p.y, p.width, p.height), sw, sh))
            }
        }
        val ip = iconParams
        val img = icon
        if (ip != null && img != null) {
            val size = dp(62)
            ip.x = ip.x.coerceIn(0, maxOf(0, sw - size))
            ip.y = ip.y.coerceIn(0, maxOf(0, sh - size))
            try { wm.updateViewLayout(img, ip) } catch (_: Exception) { }
        }
        lastSW = sw; lastSH = sh
    }

    // ------------------------------------------------------------ size rules

    /** [minW, minH, maxW, maxH] - never too small, never bigger than the screen. */
    private fun limits(sw: Int, sh: Int): IntArray {
        val maxW = (sw * 0.96f).toInt()
        val maxH = (sh * 0.92f).toInt()
        val minW = minOf(dp(200), (sw * 0.8f).toInt()).coerceAtMost(maxW)
        val minH = minOf(dp(260), (sh * 0.6f).toInt()).coerceAtMost(maxH)
        return intArrayOf(minW, minH, maxW, maxH)
    }

    private fun clampBounds(b: IntArray, sw: Int, sh: Int): IntArray {
        val l = limits(sw, sh)
        val w = b[2].coerceIn(l[0], l[2])
        val h = b[3].coerceIn(l[1], l[3])
        val x = b[0].coerceIn(0, maxOf(0, sw - w))
        val y = b[1].coerceIn(0, maxOf(0, sh - h))
        return intArrayOf(x, y, w, h)
    }

    private fun defaultBounds(sw: Int, sh: Int): IntArray {
        val l = limits(sw, sh)
        val land = sw > sh
        val w = ((if (land) 0.38f else 0.62f) * sw).toInt().coerceIn(l[0], l[2])
        val h = ((if (land) 0.82f else 0.50f) * sh).toInt().coerceIn(l[1], l[3])
        val x = if (land) dp(12) else sw - w - dp(10)
        val y = if (land) (sh - h) / 2 else dp(90)
        return clampBounds(intArrayOf(x, y, w, h), sw, sh)
    }

    private fun applyBounds(b: IntArray) {
        val p = pageParams ?: return
        val r = overlayRoot ?: return
        p.x = b[0]; p.y = b[1]; p.width = b[2]; p.height = b[3]
        try { wm.updateViewLayout(r, p) } catch (_: Exception) { }
    }

    private fun rememberBounds() {
        val p = pageParams ?: return
        savedBounds[if (lastSW > lastSH) 1 else 0] = intArrayOf(p.x, p.y, p.width, p.height)
    }

    // ------------------------------------------------------------ floating icon

    private fun showFloatingIcon() {
        if (icon != null) return
        val image = ImageView(this).apply {
            setImageResource(com.axemote.app.R.drawable.ax_official_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            contentDescription = "AX EMOTE floating icon"
        }
        val size = dp(62)
        val s = screenSize()
        val params = WindowManager.LayoutParams(size, size, overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (if (lastIconX >= 0) lastIconX else dp(18)).coerceIn(0, maxOf(0, s[0] - size))
            y = (if (lastIconY >= 0) lastIconY else dp(180)).coerceIn(0, maxOf(0, s[1] - size))
        }
        image.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
            var sw = 0; var sh = 0; var portrait = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; moved = false
                        val sc = screenSize(); sw = sc[0]; sh = sc[1]
                        portrait = sh > sw
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(5)) moved = true
                        params.x = (startX + dx).coerceIn(0, maxOf(0, sw - size))
                        params.y = (startY + dy).coerceIn(0, maxOf(0, sh - size))
                        try { wm.updateViewLayout(image, params) } catch (_: Exception) { }
                        if (moved && portrait) {
                            showRemoveTarget()
                            setTargetActive(isOverTarget(params.x + size / 2, params.y + size / 2, sw, sh))
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        lastIconX = params.x; lastIconY = params.y
                        val hit = moved && portrait && targetShown &&
                            isOverTarget(params.x + size / 2, params.y + size / 2, sw, sh)
                        hideRemoveTarget()
                        if (hit) { removeIconAndStop(); return true }
                        if (!moved) showOverlayPage()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> { hideRemoveTarget(); return true }
                }
                return false
            }
        })
        icon = image; iconParams = params
        try { wm.addView(image, params) } catch (_: Exception) { icon = null; stopSelf() }
    }

    // ------------------------------------------------------------ remove target

    private fun createRemoveTarget() {
        if (targetWin != null) return
        val holder = FrameLayout(this)
        val t = IconView(this, IconView.TARGET)
        holder.addView(t, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(40) })
        t.translationY = dp(170).toFloat() // hidden below the screen edge
        val p = WindowManager.LayoutParams(
            dp(160), dp(150), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
        try {
            wm.addView(holder, p)
            targetWin = holder; targetIcon = t
        } catch (_: Exception) { }
    }

    private fun showRemoveTarget() {
        val t = targetIcon ?: return
        if (targetShown) return
        targetShown = true
        t.animate().cancel()
        t.animate().translationY(0f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun hideRemoveTarget() {
        val t = targetIcon ?: return
        setTargetActive(false)
        if (!targetShown) return
        targetShown = false
        t.animate().cancel()
        t.animate().translationY(dp(170).toFloat()).setDuration(200).start()
    }

    private fun setTargetActive(a: Boolean) {
        val t = targetIcon ?: return
        if (a == targetActive) return
        targetActive = a
        t.active = a
        val sc = if (a) 1.25f else 1f
        t.animate().scaleX(sc).scaleY(sc).setDuration(120).start()
    }

    private fun isOverTarget(cx: Int, cy: Int, sw: Int, sh: Int): Boolean {
        val dx = (cx - sw / 2f)
        val dy = (cy - (sh - dp(72)).toFloat())
        return kotlin.math.sqrt(dx * dx + dy * dy) < dp(70)
    }

    /** Icon dropped on the close target: remove the floating icon (= overlay OFF). */
    private fun removeIconAndStop() {
        removeIcon()
        OverlayPrefs.setOn(this, false)
        stopSelf()
    }

    // ------------------------------------------------------------ overlay page (windowed card)

    private fun showOverlayPage() {
        if (overlayRoot != null) return
        iconParams?.let { lastIconX = it.x; lastIconY = it.y }
        removeIcon()

        val s = screenSize()
        val sw = s[0]; val sh = s[1]
        lastSW = sw; lastSH = sh
        val land = sw > sh
        val b = clampBounds(savedBounds[if (land) 1 else 0] ?: defaultBounds(sw, sh), sw, sh)

        // rounded card, content is clipped inside the rounded corners
        val card = FrameLayout(this).apply {
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(16).toFloat() }
            foreground = GradientDrawable().apply {
                setColor(Color.TRANSPARENT); cornerRadius = dp(16).toFloat()
                setStroke(dp(2), Color.argb(170, 255, 40, 215))
            }
            clipToOutline = true
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(column, FrameLayout.LayoutParams(-1, -1))

        // top bar: drag handle (center) + close (right)
        val topBar = FrameLayout(this).apply { setBackgroundColor(barColor) }
        column.addView(topBar, LinearLayout.LayoutParams(-1, dp(34)))
        topBar.addView(IconView(this, IconView.MOVE), FrameLayout.LayoutParams(dp(56), dp(26), Gravity.CENTER))
        val close = IconView(this, IconView.CLOSE).apply { setOnClickListener { closeOverlayPage() } }
        topBar.addView(close, FrameLayout.LayoutParams(dp(44), dp(34), Gravity.END or Gravity.CENTER_VERTICAL))

        // website area (always fits inside the card, nothing is cut)
        val holder = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        column.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))
        val web = WebViewStore.webView
        if (web != null) {
            WebViewStore.detach(web)
            holder.addView(web, FrameLayout.LayoutParams(-1, -1))
        } else {
            val fallback = TextView(this).apply { text = "Website is not ready. Open AX EMOTE SERVER first."; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
            holder.addView(fallback, FrameLayout.LayoutParams(-1, -1))
        }

        // bottom bar: resize grip (bottom-right)
        val bottomBar = FrameLayout(this).apply { setBackgroundColor(barColor) }
        column.addView(bottomBar, LinearLayout.LayoutParams(-1, dp(30)))
        val grip = IconView(this, IconView.GRIP)
        bottomBar.addView(grip, FrameLayout.LayoutParams(dp(64), -1, Gravity.END))

        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        val params = WindowManager.LayoutParams(b[2], b[3], overlayType(), flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = b[0]; y = b[1]
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        overlayRoot = card; pageParams = params; WebViewStore.overlayShowing = true

        attachDrag(topBar)
        attachResize(grip)

        try {
            wm.addView(card, params)
            WebViewStore.notifyOverlayChanged()
        } catch (_: Exception) {
            WebViewStore.webView?.let { WebViewStore.detach(it) }
            overlayRoot = null; pageParams = null; WebViewStore.overlayShowing = false
            WebViewStore.notifyOverlayChanged()
            showFloatingIcon()
        }
    }

    private fun attachDrag(handle: View) {
        var dx0 = 0f; var dy0 = 0f; var sx = 0; var sy = 0; var sw = 0; var sh = 0
        handle.setOnTouchListener { _, e ->
            val p = pageParams
            val r = overlayRoot
            if (p == null || r == null) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx0 = e.rawX; dy0 = e.rawY; sx = p.x; sy = p.y
                    val sc = screenSize(); sw = sc[0]; sh = sc[1]
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = (sx + (e.rawX - dx0)).toInt().coerceIn(0, maxOf(0, sw - p.width))
                    p.y = (sy + (e.rawY - dy0)).toInt().coerceIn(0, maxOf(0, sh - p.height))
                    try { wm.updateViewLayout(r, p) } catch (_: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { rememberBounds(); true }
                else -> false
            }
        }
    }

    private fun attachResize(grip: View) {
        var dx0 = 0f; var dy0 = 0f; var w0 = 0; var h0 = 0; var sw = 0; var sh = 0
        grip.setOnTouchListener { _, e ->
            val p = pageParams
            val r = overlayRoot
            if (p == null || r == null) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx0 = e.rawX; dy0 = e.rawY; w0 = p.width; h0 = p.height
                    val sc = screenSize(); sw = sc[0]; sh = sc[1]
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val l = limits(sw, sh)
                    val nw = (w0 + (e.rawX - dx0)).toInt().coerceIn(l[0], l[2])
                    val nh = (h0 + (e.rawY - dy0)).toInt().coerceIn(l[1], l[3])
                    p.width = nw; p.height = nh
                    if (p.x + nw > sw) p.x = sw - nw
                    if (p.y + nh > sh) p.y = sh - nh
                    try { wm.updateViewLayout(r, p) } catch (_: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    rememberBounds()
                    // let the website re-fit itself to the new size
                    WebViewStore.webView?.evaluateJavascript("window.dispatchEvent(new Event('resize'))", null)
                    true
                }
                else -> false
            }
        }
    }

    private fun closeOverlayPage() {
        val root = overlayRoot ?: return
        rememberBounds()
        val web = WebViewStore.webView
        if (web != null) WebViewStore.detach(web)
        try { wm.removeView(root) } catch (_: Exception) { }
        overlayRoot = null; pageParams = null; WebViewStore.overlayShowing = false
        WebViewStore.notifyOverlayChanged()
        showFloatingIcon()
    }

    private fun removeIcon() {
        icon?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        icon = null; iconParams = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(channelId, "AX EMOTE Overlay", NotificationManager.IMPORTANCE_LOW))
        }
    }

    @Suppress("DEPRECATION")
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder
            .setContentTitle("AX EMOTE SERVER")
            .setContentText("Floating AX icon is active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        OverlayPrefs.setOn(this, false)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        OverlayPrefs.setOn(this, false)
        closeOverlayPageIfNeeded()
        removeIcon()
        targetWin?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        targetWin = null; targetIcon = null
        stopForeground(true)
        super.onDestroy()
    }

    private fun closeOverlayPageIfNeeded() {
        overlayRoot?.let { root ->
            WebViewStore.webView?.let { WebViewStore.detach(it) }
            try { wm.removeView(root) } catch (_: Exception) { }
        }
        overlayRoot = null; pageParams = null; WebViewStore.overlayShowing = false
        WebViewStore.notifyOverlayChanged()
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onBind(intent: Intent?): IBinder? = null
}
