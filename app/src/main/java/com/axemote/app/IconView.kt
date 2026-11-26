package com.axemote.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** Small crisp vector-style icons drawn with Canvas (no font / emoji dependency). */
class IconView(context: Context, private val kind: Int) : View(context) {

    companion object {
        const val CLOSE = 0
        const val MOVE = 1
        const val GRIP = 2
        const val TARGET = 3
    }

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val accent = Color.rgb(255, 92, 225)

    /** TARGET only: true while the dragged icon hovers over it. */
    var active: Boolean = false
        set(v) { field = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        when (kind) {
            CLOSE -> {
                fill.color = Color.rgb(229, 23, 63)
                canvas.drawCircle(cx, cy, 11f * d, fill)
                line.color = Color.WHITE
                line.strokeWidth = 2f * d
                val a = 4f * d
                canvas.drawLine(cx - a, cy - a, cx + a, cy + a, line)
                canvas.drawLine(cx - a, cy + a, cx + a, cy - a, line)
            }
            MOVE -> {
                rect.set(0f, 0f, w, h)
                fill.color = Color.argb(46, 255, 255, 255)
                canvas.drawRoundRect(rect, h / 2f, h / 2f, fill)
                line.color = accent
                line.strokeWidth = 1.8f * d
                val r = 8f * d
                canvas.drawLine(cx - r, cy, cx + r, cy, line)
                canvas.drawLine(cx, cy - r, cx, cy + r, line)
                arrow(canvas, cx, cy - r, 0f, -1f)
                arrow(canvas, cx, cy + r, 0f, 1f)
                arrow(canvas, cx - r, cy, -1f, 0f)
                arrow(canvas, cx + r, cy, 1f, 0f)
            }
            TARGET -> {
                val red = Color.rgb(229, 23, 63)
                fill.color = if (active) red else Color.rgb(30, 32, 34)
                canvas.drawCircle(cx, cy, 29f * d, fill)
                line.color = red
                line.strokeWidth = 2f * d
                canvas.drawCircle(cx, cy, 29f * d, line)
                line.color = Color.WHITE
                line.strokeWidth = 2.4f * d
                val a = 9f * d
                canvas.drawLine(cx - a, cy - a, cx + a, cy + a, line)
                canvas.drawLine(cx - a, cy + a, cx + a, cy - a, line)
            }
            GRIP -> {
                line.color = accent
                line.strokeWidth = 2f * d
                val rx = w - 9f * d
                val by = h - 7f * d
                for (k in 1..3) {
                    val s = k * 4.5f * d
                    canvas.drawLine(rx - s, by, rx, by - s, line)
                }
            }
        }
    }

    private fun arrow(c: Canvas, x: Float, y: Float, ux: Float, uy: Float) {
        val a = 3f * d
        val bx = x - ux * a
        val by = y - uy * a
        c.drawLine(x, y, bx - uy * a, by + ux * a, line)
        c.drawLine(x, y, bx + uy * a, by - ux * a, line)
    }
}

/** Loading bar that fills from the left to the right (value 0..100). */
class DualProgressBar(context: Context) : View(context) {
    var value: Float = 0f
        set(v) { field = v; invalidate() }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1033") }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fillPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            Color.parseColor("#FF2BD6"), Color.parseColor("#FFE24D"), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, trackPaint)
        val fillW = w * (value / 100f).coerceIn(0f, 1f)
        if (fillW <= 0f) return
        clip.reset()
        clip.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(0f, 0f, fillW, h, fillPaint)
        canvas.restore()
    }
}

/** Icon for the OVERLAY ON/OFF button: ON = lightning bolt, OFF = power symbol (round dark badge). */
class ToggleIconDrawable(
    private val on: Boolean,
    private val iconColor: Int,
    private val badgeColor: Int,
    private val sizePx: Int
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        fill.color = badgeColor
        canvas.drawCircle(cx, cy, w / 2f, fill)
        val left = cx - w / 2f
        val top = cy - w / 2f
        if (on) {
            path.reset()
            path.moveTo(left + w * 0.57f, top + w * 0.16f)
            path.lineTo(left + w * 0.28f, top + w * 0.55f)
            path.lineTo(left + w * 0.47f, top + w * 0.55f)
            path.lineTo(left + w * 0.41f, top + w * 0.84f)
            path.lineTo(left + w * 0.72f, top + w * 0.43f)
            path.lineTo(left + w * 0.52f, top + w * 0.43f)
            path.close()
            fill.color = iconColor
            canvas.drawPath(path, fill)
        } else {
            line.color = iconColor
            line.strokeWidth = w * 0.09f
            val r = w * 0.24f
            rect.set(cx - r, cy - r + w * 0.03f, cx + r, cy + r + w * 0.03f)
            canvas.drawArc(rect, -50f, 280f, false, line)
            canvas.drawLine(cx, cy - w * 0.27f, cx, cy - w * 0.03f, line)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
