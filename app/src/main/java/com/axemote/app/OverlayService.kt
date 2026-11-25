
package com.axemote.app

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import android.webkit.WebView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var icon: ImageView? = null
    private var overlayRoot: FrameLayout? = null
    private val channelId = "ax_overlay_service"

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(7001, notification())
        showFloatingIcon()
    }

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {
        if (!OverlayPrefs.isOn(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (icon == null && overlayRoot == null) showFloatingIcon()
        return START_NOT_STICKY
    }

    private fun showFloatingIcon() {
        if (icon != null) return

        val image = ImageView(this).apply {
            setImageResource(R.drawable.ax_official_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "AX EMOTE"
        }

        val size = dp(62)
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(180)
        }

        image.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        startX = params.x
                        startY = params.y
                        moved = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()

                        if (kotlin.math.abs(dx) +
                            kotlin.math.abs(dy) > dp(5)
                        ) moved = true

                        params.x = startX + dx
                        params.y = startY + dy

                        try {
                            wm.updateViewLayout(image, params)
                        } catch (_: Exception) {}

                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!moved) showOverlayPage()
                        return true
                    }
                }
                return false
            }
        })

        icon = image
        try {
            wm.addView(image, params)
        } catch (_: Exception) {
            icon = null
            stopSelf()
        }
    }

    private fun showOverlayPage() {
        if (overlayRoot != null) return

        removeIcon()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val web = WebViewStore.webView
        if (web != null) {
            WebViewStore.detach(web)
            root.addView(web, FrameLayout.LayoutParams(-1, -1))
        }

        val close = TextView(this).apply {
            text = "×"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 20, 75))
            elevation = dp(12).toFloat()
            setOnClickListener { closeOverlayPage() }
        }

        val closeLp = FrameLayout.LayoutParams(
            dp(48), dp(48), Gravity.TOP or Gravity.END
        ).apply {
            topMargin = dp(12)
            marginEnd = dp(12)
        }

        root.addView(close, closeLp)

        val pageParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayRoot = root
        WebViewStore.overlayShowing = true

        try {
            wm.addView(root, pageParams)
        } catch (_: Exception) {
            overlayRoot = null
            WebViewStore.overlayShowing = false
            showFloatingIcon()
        }
    }

    private fun closeOverlayPage() {
        val root = overlayRoot ?: return
        try {
            wm.removeView(root)
        } catch (_: Exception) {}

        overlayRoot = null
        WebViewStore.overlayShowing = false
        showFloatingIcon()
    }

    private fun removeIcon() {
        icon?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        icon = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(
                NotificationManager::class.java
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "AX EMOTE Overlay",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channelId)
        else
            Notification.Builder(this)

        return builder
            .setContentTitle("AX EMOTE SERVER")
            .setContentText("Floating AX icon is active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        OverlayPrefs.setOn(this, false)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        overlayRoot?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        overlayRoot = null
        WebViewStore.overlayShowing = false
        removeIcon()
        OverlayPrefs.setOn(this, false)
        stopForeground(true)
        super.onDestroy()
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null
}
