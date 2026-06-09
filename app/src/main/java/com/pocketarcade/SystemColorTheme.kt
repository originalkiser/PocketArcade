package com.pocketarcade

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

/**
 * Material You / dynamic-color integration.
 *
 * "System Colors" is a 7th game board theme ([Themes.SYSTEM_COLORS_INDEX]) that
 * derives its palette from the device's wallpaper seed color (Android 12+).
 *
 * On devices below Android 12, [generate] returns null and callers fall back to Classic.
 */
object SystemColorTheme {

    /** True when the device can produce a dynamic color palette (Android 12+). */
    val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Applies DynamicColors to [activity] when "System Colors" is the active theme.
     * Must be called in [Activity.onCreate] *before* [Activity.setContentView] so
     * that Material components pick up the dynamic theme attributes.
     */
    fun applyIfActive(ctx: Context, activity: Activity) {
        if (!isAvailable) return
        if (com.pocketarcade.storage.PrefsManager.getThemeIndex(ctx) == Themes.SYSTEM_COLORS_INDEX) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    /**
     * Generates a [GameTheme] from the device's Material You palette.
     * Returns null on Android < 12 or if attribute resolution fails.
     *
     * [ctx] must be an Activity context on which [DynamicColors] has already
     * been applied (call [applyIfActive] first).
     */
    fun generate(ctx: Context, isLight: Boolean): GameTheme? {
        if (!isAvailable) return null
        return try {
            val primary    = attr(ctx, com.google.android.material.R.attr.colorPrimary,         Color.BLUE)
            val secondary  = attr(ctx, com.google.android.material.R.attr.colorSecondary,       primary)
            val tertiary   = attr(ctx, com.google.android.material.R.attr.colorTertiary,        primary)
            val surface    = attr(ctx, com.google.android.material.R.attr.colorSurface,         if (isLight) 0xFFF7F8FC.toInt() else 0xFF0F1117.toInt())
            val surfaceVar = attr(ctx, com.google.android.material.R.attr.colorSurfaceVariant,  surface)
            val onSurface  = attr(ctx, com.google.android.material.R.attr.colorOnSurface,       if (isLight) Color.BLACK else Color.WHITE)
            val outline    = attr(ctx, com.google.android.material.R.attr.colorOutline,         Color.GRAY)

            GameTheme(
                name         = "System",
                swatch       = primary,
                bg           = surface,
                surface      = surfaceVar,
                text         = onSurface,
                muted        = outline,
                player       = primary,
                rival        = secondary,
                accent       = primary,
                collect      = tertiary,
                gridDot      = primary.withAlpha(40),
                overlay      = surface.withAlpha(0xCC),
                swipeZoneBg  = if (isLight) surfaceVar.withAlpha(180) else 0
            )
        } catch (_: Exception) { null }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun attr(ctx: Context, attrRes: Int, fallback: Int): Int =
        MaterialColors.getColor(ctx, attrRes, fallback)
}
