package com.pocketarcade

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnticipateInterpolator
import kotlin.math.sin

class SplashView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onDone: (() -> Unit)? = null

    private var curtainY = 0f
    private var twinkle = 0f
    private var liftStarted = false
    private val delayHandler = Handler(Looper.getMainLooper())
    private var twinkleAnimator: ValueAnimator? = null

    private val LIGHT_COUNT = 16
    private val phases = FloatArray(LIGHT_COUNT) { i -> i * 0.48f }
    private val BULB_COLORS = intArrayOf(
        0xFFFFE066.toInt(), // warm yellow
        0xFFFF6B6B.toInt(), // coral red
        0xFFFFFFFF.toInt(), // white
        0xFFFFB347.toInt(), // amber
    )

    private val bgPaint     = Paint().apply { color = 0xFF0D0020.toInt() }
    private val curtPaint   = Paint().apply { color = 0xFF2A0048.toInt() }
    private val fold1Paint  = Paint().apply { color = 0xFF3C006A.toInt() }
    private val fold2Paint  = Paint().apply { color = 0xFF1E0038.toInt() }
    private val goldPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD4AF37.toInt() }
    private val fringePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC8962B.toInt() }
    private val bulbPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint   = Paint(Paint.ANTI_ALIAS_FLAG)

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEACC55.toInt()
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(28f, 0f, 0f, 0xFFFFD700.toInt())
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCC99FF.toInt()
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        setShadowLayer(14f, 0f, 0f, 0xFFAA44FF.toInt())
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD4AF37.toInt()
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        setShadowLayer(10f, 0f, 0f, 0xFFFFD700.toInt())
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    fun start() {
        startTwinkle()
        delayHandler.postDelayed({ if (!liftStarted) lift() }, 4000L)
    }

    fun lift() {
        if (liftStarted) return
        liftStarted = true
        delayHandler.removeCallbacksAndMessages(null)
        ValueAnimator.ofFloat(0f, -(height.toFloat() + 100f)).apply {
            duration = 1300
            interpolator = AnticipateInterpolator(1.7f)
            addUpdateListener { curtainY = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    twinkleAnimator?.cancel()
                    onDone?.invoke()
                }
            })
        }.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) lift()
        return true
    }

    // ── Drawing ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Fill only the gap above the curtain top during the anticipation dip;
        // once the curtain lifts (curtainY < 0) the game menu shows through.
        val curtainTop = curtainY - 80f
        if (curtainTop > 0f) {
            canvas.drawRect(0f, 0f, w, curtainTop, bgPaint)
        }
        canvas.save()
        canvas.translate(0f, curtainY)
        drawCurtain(canvas, right = false, w, h)
        drawCurtain(canvas, right = true, w, h)
        drawContent(canvas, w, h)
        canvas.restore()
    }

    private fun drawCurtain(canvas: Canvas, right: Boolean, w: Float, h: Float) {
        val x0 = if (right) w / 2f else 0f
        val x1 = if (right) w else w / 2f
        canvas.drawRect(x0, -80f, x1, h + 80f, curtPaint)

        // Velvet fold stripes (8 folds per side)
        val cw = w / 2f
        val foldW = cw / 8f
        for (i in 0..7) {
            val fx = x0 + i * foldW
            val fp = if (i % 2 == 0) fold1Paint else fold2Paint
            canvas.drawRect(fx, -80f, fx + foldW * 0.55f, h + 80f, fp)
        }

        // Gold header band
        canvas.drawRect(x0, 0f, x1, 11f, goldPaint)

        // Inner gold accent line
        goldPaint.alpha = 120
        canvas.drawRect(x0, 12f, x1, 14f, goldPaint)
        goldPaint.alpha = 255

        // Gold fringe at bottom
        val fw = 12f; val fh = 22f; val gap = 5f
        var fx = x0 + gap
        while (fx + fw < x1 - gap) {
            canvas.drawRoundRect(RectF(fx, h - fh, fx + fw, h - 1f), 4f, 4f, fringePaint)
            fx += fw + gap
        }
    }

    private fun drawContent(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f

        titlePaint.textSize = w * 0.148f
        subtitlePaint.textSize = w * 0.062f
        starPaint.textSize = w * 0.07f

        // Light rows (above and below title)
        val lightY1 = cy - w * 0.225f
        val lightY2 = cy + w * 0.195f
        drawLightRow(canvas, w, lightY1)
        drawLightRow(canvas, w, lightY2)

        // Stars flanking title
        val titleY = cy - w * 0.035f
        canvas.drawText("★", cx - w * 0.28f, titleY, starPaint)
        canvas.drawText("★", cx + w * 0.28f, titleY, starPaint)

        // "LOFTICE" large golden title
        canvas.drawText("LOFTICE", cx, titleY, titlePaint)

        // "PRODUCTIONS" subtitle
        canvas.drawText("PRODUCTIONS", cx, cy + w * 0.055f + subtitlePaint.textSize, subtitlePaint)
    }

    private fun drawLightRow(canvas: Canvas, w: Float, y: Float) {
        val margin = w * 0.04f
        val spacing = (w - 2 * margin) / (LIGHT_COUNT - 1)
        val br = w * 0.019f   // bulb radius
        val gr = br * 2.4f    // glow halo radius

        for (i in 0 until LIGHT_COUNT) {
            val x = margin + i * spacing
            val bright = ((sin((twinkle + phases[i]).toDouble()) * 0.45 + 0.55)).toFloat()
            val baseColor = BULB_COLORS[i % BULB_COLORS.size]

            // Outer glow halo
            glowPaint.color = baseColor
            glowPaint.alpha = (45 * bright).toInt()
            canvas.drawCircle(x, y, gr, glowPaint)

            // Main bulb
            bulbPaint.color = baseColor
            bulbPaint.alpha = (100 + 155 * bright).toInt()
            canvas.drawCircle(x, y, br, bulbPaint)

            // Specular highlight
            bulbPaint.color = Color.WHITE
            bulbPaint.alpha = (70 * bright).toInt()
            canvas.drawCircle(x - br * 0.3f, y - br * 0.35f, br * 0.33f, bulbPaint)
        }
    }

    private fun startTwinkle() {
        twinkleAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 1700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { twinkle = it.animatedValue as Float; invalidate() }
        }.also { it.start() }
    }
}
