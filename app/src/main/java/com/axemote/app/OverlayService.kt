package com.axemote.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var icon: ImageView? = null
    private var overlayRoot: FrameLayout? = null
    private var iconParams: WindowManager.LayoutParams? = null
    private var pageParams: WindowManager.LayoutParams? = null
    private val channelId = "ax_overlay_service"

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
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

    private fun showFloatingIcon() {
        if (icon != null) return
        val image = ImageView(this).apply {
            setImageResource(com.axemote.app.R.drawable.ax_official_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            contentDescription = "AX EMOTE floating icon"
        }
        val size = dp(62)
        val params = WindowManager.LayoutParams(size, size, overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18); y = dp(180)
        }
        image.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; moved = false; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(5)) moved = true
                        params.x = startX + dx; params.y = startY + dy
                        try { wm.updateViewLayout(image, params) } catch (_: Exception) { }
                        return true
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) showOverlayPage(); return true }
                }
                return false
            }
        })
        icon = image; iconParams = params
        try { wm.addView(image, params) } catch (_: Exception) { icon = null; stopSelf() }
    }

    private fun showOverlayPage() {
        if (overlayRoot != null) return
        removeIcon()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val web = WebViewStore.webView
        if (web != null) {
            WebViewStore.detach(web)
            root.addView(web, FrameLayout.LayoutParams(-1, -1))
        } else {
            val fallback = TextView(this).apply { text = "Website is not ready. Open AX EMOTE SERVER first."; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
            root.addView(fallback, FrameLayout.LayoutParams(-1, -1))
        }
        val close = TextView(this).apply {
            text = "×"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 20, 75)); elevation = dp(12).toFloat()
            setOnClickListener { closeOverlayPage() }
        }
        val closeLp = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply { topMargin = dp(12); marginEnd = dp(12) }
        root.addView(close, closeLp)
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, overlayType(), WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        overlayRoot = root; pageParams = params; WebViewStore.overlayShowing = true
        try { wm.addView(root, params) } catch (_: Exception) { overlayRoot = null; WebViewStore.overlayShowing = false; showFloatingIcon() }
    }

    private fun closeOverlayPage() {
        val root = overlayRoot ?: return
        val web = WebViewStore.webView
        if (web != null) WebViewStore.detach(web)
        try { wm.removeView(root) } catch (_: Exception) { }
        overlayRoot = null; pageParams = null; WebViewStore.overlayShowing = false
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
        closeOverlayPageIfNeeded()
        removeIcon()
        OverlayPrefs.setOn(this, false)
        stopForeground(true)
        super.onDestroy()
    }

    private fun closeOverlayPageIfNeeded() {
        overlayRoot?.let { root ->
            WebViewStore.webView?.let { WebViewStore.detach(it) }
            try { wm.removeView(root) } catch (_: Exception) { }
        }
        overlayRoot = null; WebViewStore.overlayShowing = false
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onBind(intent: Intent?): IBinder? = null
}
