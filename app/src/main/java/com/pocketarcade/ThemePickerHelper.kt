package com.pocketarcade

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.storage.PrefsManager

/**
 * Draws a split-oval (left half = c1, right half = c2) with an optional stroke ring.
 * Shared by ThemePickerHelper, SnakeActivity, and SettingsActivity.
 */
fun splitOvalDrawable(c1: Int, c2: Int, strokeColor: Int, strokePx: Int): Drawable {
    return object : Drawable() {
        private val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c1 }
        private val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c2 }
        private val ps = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = strokeColor
            strokeWidth = strokePx.toFloat()
        }
        override fun draw(canvas: Canvas) {
            val r = RectF(bounds)
            canvas.drawArc(r, 90f, 180f, true, p1)
            canvas.drawArc(r, 270f, 180f, true, p2)
            if (strokePx > 0) {
                val inset = strokePx / 2f
                canvas.drawOval(
                    RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset), ps
                )
            }
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

/**
 * Rainbow sweep-gradient oval for the "System Colors" theme swatch.
 * On Android < 12 the swatch is rendered semi-transparent to signal unavailability.
 */
fun rainbowOvalDrawable(strokePx: Int): Drawable {
    val colors = intArrayOf(
        Color.parseColor("#FF0000"), Color.parseColor("#FFFF00"),
        Color.parseColor("#00FF00"), Color.parseColor("#00FFFF"),
        Color.parseColor("#0000FF"), Color.parseColor("#FF00FF"),
        Color.parseColor("#FF0000")
    )
    return object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = strokePx.toFloat()
        }
        override fun draw(canvas: Canvas) {
            val r = RectF(bounds)
            val cx = r.centerX(); val cy = r.centerY()
            paint.shader = SweepGradient(cx, cy, colors, null)
            canvas.drawOval(r, paint)
            if (strokePx > 0) {
                val inset = strokePx / 2f
                canvas.drawOval(
                    RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset), stroke
                )
            }
            if (!SystemColorTheme.isAvailable) {
                paint.shader = null; paint.color = 0x88000000.toInt()
                canvas.drawOval(r, paint)
            }
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

fun showThemePickerDialog(
    activity: AppCompatActivity,
    game: String,
    onApplied: () -> Unit
) {
    val view = activity.layoutInflater.inflate(R.layout.dialog_theme_picker, null)

    val tvThemeName = view.findViewById<TextView>(R.id.tvThemeName)
    val swatchIds = listOf(
        R.id.swatch0, R.id.swatch1, R.id.swatch2,
        R.id.swatch3, R.id.swatch4, R.id.swatch5
    )

    // Always show the currently effective theme (game-specific if set, else global fallback).
    var localIndex = ThemeManager.effectiveThemeIndex(activity, game)

    fun refreshSwatches() {
        tvThemeName.text = Themes.ALL[localIndex].first.name
        val d = activity.resources.displayMetrics.density
        val strokePx = (3 * d).toInt()
        swatchIds.forEachIndexed { i, id ->
            val sw = view.findViewById<View>(id)
            val theme = Themes.ALL[i].first
            val selected = i == localIndex
            sw.background = splitOvalDrawable(
                theme.swatch, theme.rival,
                if (selected) Color.WHITE else 0, if (selected) strokePx else 0
            )
        }
    }

    swatchIds.forEachIndexed { i, id ->
        view.findViewById<View>(id).setOnClickListener {
            localIndex = i
            // Always save as a game-specific override.
            PrefsManager.setGameThemeIndex(activity, game, i)
            PrefsManager.setGameUsingGlobalTheme(activity, game, false)
            refreshSwatches()
            onApplied()
        }
    }

    refreshSwatches()

    val dialog = AlertDialog.Builder(activity)
        .setView(view)
        .setPositiveButton("DONE", null)
        .create()
    dialog.window?.setBackgroundDrawableResource(R.color.surface)
    dialog.show()
}
