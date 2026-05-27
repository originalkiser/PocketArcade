package com.pocketarcade

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class CrtOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val scanlinePaint = Paint().apply {
        color = 0xFF000000.toInt()
        alpha = 18
    }
    private val vignettePaint = Paint()
    private var vignetteShader: RadialGradient? = null

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        vignetteShader = RadialGradient(
            w / 2f, h / 2f,
            maxOf(w, h) * 0.72f,
            intArrayOf(0x00000000, 0x00000000, 0x55000000),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        vignettePaint.shader = vignetteShader
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Scanlines: 1px dark bar every 2px
        var y = 0f
        while (y < h) {
            canvas.drawRect(0f, y, w, y + 1f, scanlinePaint)
            y += 3f
        }

        // Vignette
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }
}
