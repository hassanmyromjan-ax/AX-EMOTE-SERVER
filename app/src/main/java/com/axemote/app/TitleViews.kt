package com.axemote.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One big title letter: golden gradient fill, deep-indigo outline, magenta outer outline,
 * slanted heavy font. Drawn with Canvas only (no image files).
 */
class TitleLetterView(context: Context, private val ch: String, private val sizePx: Float) : View(context) {

    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        textSkewX = -0.2f
    }
    private val pad = sizePx * 0.10f
    private val tw = base.measureText(ch)
    private val fm = base.fontMetrics

    private val shadowP = Paint(base).apply {
        style = Paint.Style.FILL_AND_STROKE; strokeWidth = sizePx * 0.17f
        strokeJoin = Paint.Join.ROUND; color = Color.parseColor("#06021A")
    }
    private val outerP = Paint(base).apply {
        style = Paint.Style.STROKE; strokeWidth = sizePx * 0.16f
        strokeJoin = Paint.Join.ROUND; color = Color.parseColor("#FF2BD6")
    }
    private val midP = Paint(base).apply {
        style = Paint.Style.STROKE; strokeWidth = sizePx * 0.09f
        strokeJoin = Paint.Join.ROUND; color = Color.parseColor("#1B0C5E")
    }
    private val fillP = Paint(base).apply { style = Paint.Style.FILL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = Math.ceil((tw + pad * 2 + sizePx * 0.14f).toDouble()).toInt()
        val h = Math.ceil((fm.descent - fm.ascent + pad * 2).toDouble()).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val x = pad
        val y = pad - fm.ascent
        if (fillP.shader == null) {
            fillP.shader = LinearGradient(
                0f, y - sizePx * 0.74f, 0f, y,
                intArrayOf(Color.parseColor("#FFFBD2"), Color.parseColor("#FFE24D"), Color.parseColor("#FFAE00")),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawText(ch, x + sizePx * 0.04f, y + sizePx * 0.06f, shadowP)
        canvas.drawText(ch, x, y, outerP)
        canvas.drawText(ch, x, y, midP)
        canvas.drawText(ch, x, y, fillP)
    }
}

/** Small vector icons next to "AX": a shield and a game controller. */
class MiniIconView(context: Context, private val kind: Int) : View(context) {

    companion object {
        const val SHIELD = 0
        const val PAD = 1
    }

    private val gold = Color.parseColor("#FFD54F")
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND; color = gold
    }
    private val path = Path()
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        strokeP.strokeWidth = w * 0.07f
        if (kind == SHIELD) {
            path.reset()
            path.moveTo(w * 0.50f, h * 0.04f)
            path.lineTo(w * 0.92f, h * 0.16f)
            path.lineTo(w * 0.92f, h * 0.52f)
            path.quadTo(w * 0.92f, h * 0.82f, w * 0.50f, h * 0.97f)
            path.quadTo(w * 0.08f, h * 0.82f, w * 0.08f, h * 0.52f)
            path.lineTo(w * 0.08f, h * 0.16f)
            path.close()
            fillP.color = Color.parseColor("#E53935")
            c.drawPath(path, fillP)
            c.save()
            c.clipPath(path)
            fillP.color = Color.WHITE
            c.drawRect(w * 0.22f, 0f, w * 0.42f, h, fillP)
            c.restore()
            c.drawPath(path, strokeP)
        } else {
            val navy = Color.parseColor("#1A1050")
            // grips
            fillP.color = navy
            c.drawCircle(w * 0.24f, h * 0.74f, h * 0.17f, fillP)
            c.drawCircle(w * 0.76f, h * 0.74f, h * 0.17f, fillP)
            c.drawCircle(w * 0.24f, h * 0.74f, h * 0.17f, strokeP)
            c.drawCircle(w * 0.76f, h * 0.74f, h * 0.17f, strokeP)
            // body
            rect.set(w * 0.04f, h * 0.16f, w * 0.96f, h * 0.70f)
            c.drawRoundRect(rect, h * 0.25f, h * 0.25f, fillP)
            c.drawRoundRect(rect, h * 0.25f, h * 0.25f, strokeP)
            // d-pad
            strokeP.strokeWidth = w * 0.05f
            c.drawLine(w * 0.20f, h * 0.43f, w * 0.34f, h * 0.43f, strokeP)
            c.drawLine(w * 0.27f, h * 0.31f, w * 0.27f, h * 0.55f, strokeP)
            // buttons
            fillP.color = gold
            c.drawCircle(w * 0.74f, h * 0.34f, h * 0.05f, fillP)
            c.drawCircle(w * 0.84f, h * 0.45f, h * 0.05f, fillP)
            c.drawCircle(w * 0.64f, h * 0.45f, h * 0.05f, fillP)
        }
    }
}

/**
 * Black animated background for the title screen: breathing green/cyan rays,
 * twinkling pink/cyan/purple dots and spiky purple-magenta corner shards (all drawn, no images).
 */
class SplashFxView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val rnd = java.util.Random(11)
    private val nRays = 44
    private val rayAng = FloatArray(nRays)
    private val rayLen = FloatArray(nRays)
    private val rayPh = FloatArray(nRays)
    private val rayCol = IntArray(nRays)
    private val nDots = 60
    private val dotX = FloatArray(nDots)
    private val dotY = FloatArray(nDots)
    private val dotR = FloatArray(nDots)
    private val dotPh = FloatArray(nDots)
    private val dotCol = IntArray(nDots)

    private var phase = 0f
    private var intro = 0f
    private val anims = ArrayList<ValueAnimator>()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#2B1070") }
    private val shardLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = Color.parseColor("#FF2BD6"); strokeWidth = 2f * d
    }
    private val path = Path()

    init {
        val rayColors = intArrayOf(Color.parseColor("#2EFF8A"), Color.parseColor("#27E6FF"), Color.parseColor("#1FD6A5"))
        for (i in 0 until nRays) {
            rayAng[i] = (i * 2.0 * PI / nRays).toFloat() + rnd.nextFloat() * 0.05f
            rayLen[i] = 0.35f + rnd.nextFloat() * 0.65f
            rayPh[i] = rnd.nextFloat()
            rayCol[i] = rayColors[i % rayColors.size]
        }
        val dotColors = intArrayOf(Color.parseColor("#FF4FD8"), Color.parseColor("#27E6FF"), Color.parseColor("#9B59FF"), Color.parseColor("#FFE24D"))
        for (i in 0 until nDots) {
            dotX[i] = rnd.nextFloat()
            dotY[i] = rnd.nextFloat()
            dotR[i] = 0.8f + rnd.nextFloat() * 1.8f
            dotPh[i] = rnd.nextFloat()
            dotCol[i] = dotColors[i % dotColors.size]
        }
    }

    fun start() {
        stop()
        val loop = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        }
        val intro = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            addUpdateListener { this@SplashFxView.intro = it.animatedValue as Float }
        }
        anims.add(loop); anims.add(intro)
        loop.start(); intro.start()
    }

    fun stop() {
        anims.forEach { it.cancel() }
        anims.clear()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val m = minOf(w, h)
        val cx = w / 2f
        val cy = h * 0.42f
        val twoPi = (2.0 * PI).toFloat()

        // rays
        val r0 = m * 0.22f
        for (i in 0 until nRays) {
            val breathe = 0.6f + 0.4f * sin(twoPi * (phase + rayPh[i]))
            val r1 = r0 + rayLen[i] * m * 0.6f * breathe * intro
            linePaint.color = rayCol[i]
            linePaint.alpha = (110 * intro).toInt()
            linePaint.strokeWidth = (if (i % 3 == 0) 2.2f else 1.3f) * d
            val ca = cos(rayAng[i]); val sa = sin(rayAng[i])
            canvas.drawLine(cx + ca * r0, cy + sa * r0, cx + ca * r1, cy + sa * r1, linePaint)
        }

        // twinkling dots
        for (i in 0 until nDots) {
            val tw = 0.5f + 0.5f * sin(twoPi * (phase * 2f + dotPh[i]))
            dotPaint.color = dotCol[i]
            dotPaint.alpha = ((50 + 190 * tw) * intro).toInt().coerceIn(0, 255)
            canvas.drawCircle(dotX[i] * w, dotY[i] * h, dotR[i] * d, dotPaint)
        }

        // corner shards slide in from outside
        val slide = 1f - intro
        shard(canvas, 0f, 0f, 1f, 1f, m * 0.30f, slide)
        shard(canvas, w, 0f, -1f, 1f, m * 0.24f, slide)
        shard(canvas, 0f, h, 1f, -1f, m * 0.24f, slide)
        shard(canvas, w, h, -1f, -1f, m * 0.30f, slide)
        shard(canvas, 0f, 0f, 1f, 1f, m * 0.16f, slide)
        shard(canvas, w, h, -1f, -1f, m * 0.16f, slide)
    }

    private fun shard(c: Canvas, x0: Float, y0: Float, ix: Float, iy: Float, size: Float, slide: Float) {
        val ox = -ix * size * slide
        val oy = -iy * size * slide
        path.reset()
        path.moveTo(x0 + ox, y0 + oy)
        path.lineTo(x0 + ix * size + ox, y0 + iy * size * 0.22f + oy)
        path.lineTo(x0 + ix * size * 0.22f + ox, y0 + iy * size + oy)
        path.close()
        c.drawPath(path, shardFill)
        c.drawPath(path, shardLine)
    }
}
