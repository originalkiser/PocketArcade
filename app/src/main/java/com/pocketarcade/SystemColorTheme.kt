package com.pocketarcade

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors

/**
 * Material You / dynamic-color integration.
 *
 * "System Colors" is a 7th game board theme ([Themes.SYSTEM_COLORS_INDEX]) that
 * derives its palette from the device's wallpaper seed color (Android 12+).
 *
 * On devices below Android 12, [generate] returns null and callers fall back to Classic.
 *
 * Implementation note: rather than relying on Material attribute resolution (which
 * depends on the base theme declaring colorTertiary, colorOutline, etc.), we read the
 * Android 12 system color tonal palette directly from [android.R.color.system_accent1_*]
 * and [android.R.color.system_neutral1_*] resources.  These are always present on
 * API 31+ regardless of which Material variant the app theme extends, and they update
 * automatically when the device wallpaper or color scheme changes.
 */
object SystemColorTheme {

    /** True when the device can produce a dynamic color palette (Android 12+). */
    val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Applies DynamicColors to [activity] so that any Material components in the
     * layout automatically pick up the wallpaper-derived palette.
     * Must be called in [Activity.onCreate] *before* [Activity.setContentView].
     */
    fun applyIfActive(ctx: Context, activity: Activity) {
        if (!isAvailable) return
        if (com.pocketarcade.storage.PrefsManager.getThemeIndex(ctx) == Themes.SYSTEM_COLORS_INDEX) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    /**
     * Generates a [GameTheme] directly from the Android 12 tonal palette resources.
     * Returns null on Android < 12 or if any resource lookup fails.
     *
     * Tone scale used (0 = white → 1000 = black, approximately):
     *   light mode  — darker tones (700) for primary/secondary so text is legible
     *   dark  mode  — lighter tones (200) for primary/secondary so they glow on dark bg
     */
    fun generate(ctx: Context, isLight: Boolean): GameTheme? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            fun sys(res: Int) = ContextCompat.getColor(ctx, res)

            // ── Accent (hue from wallpaper) ──────────────────────────────────────
            val primary   = if (isLight) sys(android.R.color.system_accent1_700)
                            else         sys(android.R.color.system_accent1_200)
            val secondary = if (isLight) sys(android.R.color.system_accent2_700)
                            else         sys(android.R.color.system_accent2_200)
            val tertiary  = if (isLight) sys(android.R.color.system_accent3_700)
                            else         sys(android.R.color.system_accent3_200)

            // ── Neutral surfaces (low-chroma version of accent hue) ───────────────
            val bg      = if (isLight) sys(android.R.color.system_neutral1_50)
                          else         sys(android.R.color.system_neutral1_900)
            val surface = if (isLight) sys(android.R.color.system_neutral1_100)
                          else         sys(android.R.color.system_neutral1_800)
            val text    = if (isLight) sys(android.R.color.system_neutral1_900)
                          else         sys(android.R.color.system_neutral1_50)
            val muted   = if (isLight) sys(android.R.color.system_neutral2_500)
                          else         sys(android.R.color.system_neutral2_400)

            GameTheme(
                name        = "System",
                swatch      = primary,
                bg          = bg,
                surface     = surface,
                text        = text,
                muted       = muted,
                player      = primary,
                rival       = secondary,
                accent      = primary,
                collect     = tertiary,
                gridDot     = primary.withAlpha(40),
                overlay     = bg.withAlpha(0xCC),
                swipeZoneBg = if (isLight) surface.withAlpha(180) else 0
            )
        } catch (_: Exception) { null }
    }
}
