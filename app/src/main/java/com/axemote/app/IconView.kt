package com.axemote.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Small crisp vector-style icons drawn with Canvas (no font / emoji dependency). */
class IconView(context: Context, private val kind: Int) : View(context) {

    companion object {
        const val CLOSE = 0
        const val MOVE = 1
        const val GRIP = 2
    }

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val accent = Color.rgb(255, 92, 225)

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
