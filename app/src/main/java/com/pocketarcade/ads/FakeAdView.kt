package com.pocketarcade.ads

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.pocketarcade.R

class FakeAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val ads: Array<String> = context.resources.getStringArray(R.array.fake_ads)
    private var currentIndex = 0
    private var slideOffset = 0f  // +width = off-screen right, -width = off-screen left

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#1a1d2e")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#3a4a6e")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#f1c40f")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e74c3c")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }

    private val handler = Handler(Looper.getMainLooper())
    private val rotateRunnable = object : Runnable {
        override fun run() {
            animateRotation()
            handler.postDelayed(this, 18_000L)
        }
    }

    init {
        currentIndex = (0 until ads.size).random()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.postDelayed(rotateRunnable, 18_000L)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(rotateRunnable)
    }

    private fun animateRotation() {
        val w = width.toFloat()
        if (w == 0f) {
            currentIndex = (currentIndex + 1) % ads.size
            return
        }
        // Slide current ad out to the left
        ValueAnimator.ofFloat(0f, -w).apply {
            duration = 300
            addUpdateListener { slideOffset = it.animatedValue as Float; invalidate() }
            doOnEnd {
                currentIndex = (currentIndex + 1) % ads.size
                slideOffset = w  // new ad starts off-screen to the right
                invalidate()
                // Slide new ad in from the right
                ValueAnimator.ofFloat(w, 0f).apply {
                    duration = 300
                    addUpdateListener { slideOffset = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(0f, 0f, w, h, borderPaint)

        canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        canvas.translate(slideOffset, 0f)

        val ad = ads[currentIndex]
        val cx = w / 2f
        val cy = h / 2f

        textPaint.textSize = h * 0.48f
        val maxWidth = w - 72f
        if (textPaint.measureText(ad) > maxWidth) {
            textPaint.textSize = textPaint.textSize * (maxWidth / textPaint.measureText(ad))
        }

        val textMetrics = textPaint.fontMetrics
        val textHeight = textMetrics.descent - textMetrics.ascent
        canvas.drawText(ad, cx, cy - textMetrics.ascent - textHeight / 2, textPaint)

        canvas.restore()

        tagPaint.textSize = h * 0.30f
        canvas.drawText("AD ", 8f, h - 6f, tagPaint)
    }
}

private fun ValueAnimator.doOnEnd(block: () -> Unit) {
    addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) = block()
    })
}
