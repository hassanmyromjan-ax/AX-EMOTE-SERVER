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
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.webkit.WebView
import kotlin.math.max
import kotlin.math.min

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var icon: ImageView? = null
    private var overlayRoot: FrameLayout? = null
    private var iconParams: WindowManager.LayoutParams? = null
    private var pageParams: WindowManager.LayoutParams? = null
    private var resizeHandle: TextView? = null
    private var dragHandle: TextView? = null
    private val channelId = "ax_overlay_service"

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7001, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7001, notification())
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
            setImageResource(R.drawable.ax_official_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            contentDescription = "AX EMOTE floating icon"
        }
        val params = WindowManager.LayoutParams(dp(62), dp(62), overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = dp(18); y = dp(180)
        }
        image.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; moved = false; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX-downX).toInt(); val dy = (event.rawY-downY).toInt()
                        if (kotlin.math.abs(dx)+kotlin.math.abs(dy)>dp(5)) moved = true
                        params.x = startX+dx; params.y = startY+dy
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
        val bounds = usableBounds()
        val portrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val width = min(dp(if (portrait) 360 else 560), (bounds.first * 0.90f).toInt())
        val height = min(dp(if (portrait) 520 else 360), (bounds.second * 0.82f).toInt())
        val root = FrameLayout(this).apply {
            background = rounded(0xFF050509.toInt(), dp(15))
            clipToOutline = true
            elevation = dp(16).toFloat()
        }
        val web = WebViewStore.webView
        if (web != null) {
            WebViewStore.detach(web)
            root.addView(web, FrameLayout.LayoutParams(-1, -1))
        } else {
            val fallback = TextView(this).apply { text = "Website is not ready. Open AX EMOTE SERVER first."; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
            root.addView(fallback, FrameLayout.LayoutParams(-1, -1))
        }

        val close = TextView(this).apply {
            text = "×"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(0xEE261B43.toInt(), dp(22)); elevation = dp(12).toFloat()
            setOnClickListener { closeOverlayPage() }
        }
        root.addView(close, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP or Gravity.END).apply { topMargin = dp(8); marginEnd = dp(8) })

        val drag = TextView(this).apply {
            text = "✥"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(0xEE261B43.toInt(), dp(22)); elevation = dp(12).toFloat()
        }
        root.addView(drag, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) })

        val resize = TextView(this).apply {
            text = "⤢"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(0xEEB20A68.toInt(), dp(22)); elevation = dp(12).toFloat()
        }
        root.addView(resize, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END))

        val params = WindowManager.LayoutParams(width, height, overlayType(), WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = max(dp(4), (bounds.first-width)/2)
            y = max(dp(28), (bounds.second-height)/3)
        }
        attachDrag(drag, root, params)
        attachResize(resize, root, params)
        overlayRoot = root; pageParams = params; dragHandle = drag; resizeHandle = resize
        WebViewStore.overlayShowing = true
        try { wm.addView(root, params) } catch (_: Exception) {
            overlayRoot = null; pageParams = null; WebViewStore.overlayShowing = false; showFloatingIcon()
        }
    }

    private fun attachDrag(handle: View, root: View, params: WindowManager.LayoutParams) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            var sx=0f; var sy=0f; var px=0; var py=0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when(e.action) {
                    MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; px=params.x; py=params.y; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val b=usableBounds(); params.x=(px+(e.rawX-sx).toInt()).coerceIn(0,max(0,b.first-params.width)); params.y=(py+(e.rawY-sy).toInt()).coerceIn(0,max(0,b.second-params.height))
                        update(root,params); return true
                    }
                    MotionEvent.ACTION_UP -> return true
                }; return false
            }
        })
    }

    private fun attachResize(handle: View, root: View, params: WindowManager.LayoutParams) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            var sx=0f; var sy=0f; var sw=0; var sh=0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when(e.action) {
                    MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; sw=params.width; sh=params.height; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val b=usableBounds(); val portrait=resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
                        val minW=dp(240).coerceAtMost(b.first); val minH=dp(220).coerceAtMost(b.second)
                        val maxW=(b.first*0.96f).toInt(); val maxH=(b.second*0.94f).toInt()
                        params.width=(sw+(e.rawX-sx).toInt()).coerceIn(minW,maxW)
                        params.height=(sh+(e.rawY-sy).toInt()).coerceIn(minH,maxH)
                        update(root,params); return true
                    }
                    MotionEvent.ACTION_UP -> return true
                }; return false
            }
        })
    }

    private fun update(root: View, params: WindowManager.LayoutParams) {
        try { wm.updateViewLayout(root,params) } catch (_: Exception) { }
    }

    private fun usableBounds(): Pair<Int,Int> {
        val dm=resources.displayMetrics
        var w=dm.widthPixels; var h=dm.heightPixels
        if (Build.VERSION.SDK_INT >= 30) {
            val r=wm.currentWindowMetrics.bounds
            w=r.width(); h=r.height()
        }
        return Pair(w,h)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val root=overlayRoot ?: return
        val p=pageParams ?: return
        val b=usableBounds(); val portrait=newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE
        val maxW=(b.first*0.96f).toInt(); val maxH=(b.second*0.94f).toInt()
        p.width=min(p.width,maxW).coerceAtLeast(min(dp(240),maxW))
        p.height=min(p.height,maxH).coerceAtLeast(min(dp(220),maxH))
        p.x=p.x.coerceIn(0,max(0,b.first-p.width)); p.y=p.y.coerceIn(0,max(0,b.second-p.height))
        update(root,p)
    }

    private fun closeOverlayPage() {
        val root=overlayRoot ?: return
        WebViewStore.webView?.let { WebViewStore.detach(it) }
        try { wm.removeView(root) } catch (_: Exception) { }
        overlayRoot=null; pageParams=null; dragHandle=null; resizeHandle=null; WebViewStore.overlayShowing=false
        showFloatingIcon()
    }

    private fun removeIcon() {
        icon?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        icon=null; iconParams=null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channelId,"AX EMOTE Overlay",NotificationManager.IMPORTANCE_LOW))
    }

    @Suppress("DEPRECATION")
    private fun notification(): Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,channelId) else Notification.Builder(this)
        return b.setContentTitle("AX EMOTE SERVER").setContentText("Floating AX overlay is active").setSmallIcon(android.R.drawable.ic_menu_view).setContentIntent(open).setOngoing(true).build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) { OverlayPrefs.setOn(this,false); stopSelf(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() { closeOverlayPageIfNeeded(); removeIcon(); OverlayPrefs.setOn(this,false); stopForeground(true); super.onDestroy() }
    private fun closeOverlayPageIfNeeded() {
        overlayRoot?.let { root -> WebViewStore.webView?.let { WebViewStore.detach(it) }; try { wm.removeView(root) } catch (_: Exception) { } }
        overlayRoot=null; pageParams=null; WebViewStore.overlayShowing=false
    }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply { setColor(color); cornerRadius=radius.toFloat(); setStroke(dp(1),0x44FFFFFF) }
    private fun overlayType()=if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onBind(intent:Intent?):IBinder?=null
}
