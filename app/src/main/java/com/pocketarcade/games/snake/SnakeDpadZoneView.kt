package com.pocketarcade.games.snake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Full-area d-pad control divided into four triangular tap zones by two diagonal
 * lines running corner-to-corner (forming an X pattern).
 *
 * Zone detection (normalised 0..1 coordinates):
 *   Top    — normY < normX  AND normY < 1−normX
 *   Bottom — normY > normX  AND normY > 1−normX
 *   Left   — normY > normX  AND normY < 1−normX
 *   Right  — normY < normX  AND normY > 1−normX
 *
 * The aspect ratio of the control area determines how wide/tall each triangle
 * is — top/bottom are naturally wider on a landscape-style control zone.
 */
class SnakeDpadZoneView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Callbacks ──────────────────────────────────────────────────────────────
    var onUp:    (() -> Unit)? = null
    var onDown:  (() -> Unit)? = null
    var onLeft:  (() -> Unit)? = null
    var onRight: (() -> Unit)? = null

    // ── Theme colours (updated by applyTheme) ──────────────────────────────────
    private var lineColor  = Color.WHITE
    private var glyphColor = Color.WHITE

    fun applyTheme(accent: Int, text: Int) {
        // Divider lines: low-opacity text colour
        lineColor  = Color.argb(50,
            Color.red(text), Color.green(text), Color.blue(text))
        // Glyphs: medium-opacity accent colour
        glyphColor = Color.argb(160,
            Color.red(accent), Color.green(accent), Color.blue(accent))
        invalidate()
    }

    // ── Paints ─────────────────────────────────────────────────────────────────
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val dp = resources.displayMetrics.density
        linePaint.strokeWidth = 1f * dp
        // Glyph size: ~14% of the shorter dimension so it's clearly readable
        glyphPaint.textSize = minOf(w, h) * 0.18f
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // ── Diagonal dividers ────────────────────────────────────────────────
        linePaint.color = lineColor
        canvas.drawLine(0f, 0f, w, h, linePaint)   // top-left → bottom-right
        canvas.drawLine(w, 0f, 0f, h, linePaint)   // top-right → bottom-left

        // ── Direction glyphs ─────────────────────────────────────────────────
        // Placed at ¼ of the way from each edge toward the centre, so they sit
        // visually in the middle of each triangle regardless of aspect ratio.
        glyphPaint.color = glyphColor
        val cy = glyphPaint.textSize * 0.35f   // vertical offset to visually centre text

        // Up ▲ — upper triangle, centred horizontally
        canvas.drawText("▲", w / 2f, h * 0.28f + cy, glyphPaint)
        // Down ▼ — lower triangle
        canvas.drawText("▼", w / 2f, h * 0.72f + cy, glyphPaint)
        // Left ◀ — left triangle
        canvas.drawText("◀", w * 0.20f, h / 2f + cy, glyphPaint)
        // Right ▶ — right triangle
        canvas.drawText("▶", w * 0.80f, h / 2f + cy, glyphPaint)
    }

    // ── Touch ──────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val nx = event.x / width     // normalised 0..1
            val ny = event.y / height

            when {
                ny < nx && ny < 1f - nx -> onUp?.invoke()
                ny > nx && ny > 1f - nx -> onDown?.invoke()
                ny > nx && ny < 1f - nx -> onLeft?.invoke()
                else                    -> onRight?.invoke()
            }
        }
        return true
    }
}
