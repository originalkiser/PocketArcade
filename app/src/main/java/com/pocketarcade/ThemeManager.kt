package com.pocketarcade

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.pocketarcade.storage.PrefsManager

object ThemeManager {

    // ── Display mode ───────────────────────────────────────────────────────────

    fun getDisplayMode(ctx: Context) = PrefsManager.getDisplayMode(ctx)

    /**
     * Persists the new mode and applies it via AppCompatDelegate. Active
     * activities will recreate automatically to pick up the new configuration.
     */
    fun setDisplayMode(ctx: Context, mode: PrefsManager.DisplayMode) {
        PrefsManager.setDisplayMode(ctx, mode)
        applyDisplayMode(ctx)
    }

    /**
     * Syncs AppCompatDelegate's night-mode setting with the stored preference.
     * Call once from [PocketArcadeApp.onCreate]; after that the delegate handles
     * Activity recreation on subsequent changes.
     */
    fun applyDisplayMode(ctx: Context) {
        val target = when (PrefsManager.getDisplayMode(ctx)) {
            PrefsManager.DisplayMode.DARK   -> AppCompatDelegate.MODE_NIGHT_YES
            PrefsManager.DisplayMode.LIGHT  -> AppCompatDelegate.MODE_NIGHT_NO
            PrefsManager.DisplayMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != target) {
            AppCompatDelegate.setDefaultNightMode(target)
        }
    }

    /**
     * Returns true when the UI should currently render in light mode, correctly
     * handling all three [PrefsManager.DisplayMode] cases including SYSTEM
     * (reads the live device configuration from [ctx]).
     */
    fun isEffectivelyLight(ctx: Context): Boolean = when (PrefsManager.getDisplayMode(ctx)) {
        PrefsManager.DisplayMode.LIGHT  -> true
        PrefsManager.DisplayMode.DARK   -> false
        PrefsManager.DisplayMode.SYSTEM ->
            (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
                Configuration.UI_MODE_NIGHT_YES
    }

    /** Legacy single-flag accessor — delegates to [isEffectivelyLight]. */
    fun isLightMode(ctx: Context): Boolean = isEffectivelyLight(ctx)

    /**
     * Legacy binary setter — used by per-game light-mode toggle buttons.
     * Saves an explicit DARK or LIGHT mode (overrides SYSTEM), then applies it
     * via AppCompatDelegate (which recreates the calling Activity).
     */
    fun setLightMode(ctx: Context, light: Boolean) =
        setDisplayMode(ctx, if (light) PrefsManager.DisplayMode.LIGHT else PrefsManager.DisplayMode.DARK)

    // ── App background theme ────────────────────────────────────────────────

    fun themeIndex(ctx: Context): Int = PrefsManager.getThemeIndex(ctx)

    fun setThemeIndex(ctx: Context, index: Int) = PrefsManager.setThemeIndex(ctx, index)

    fun bgThemeIndex(ctx: Context): Int = PrefsManager.getBgThemeIndex(ctx)
    fun setBgThemeIndex(ctx: Context, index: Int) = PrefsManager.setBgThemeIndex(ctx, index)

    /**
     * Background color for shell activities (menus, settings, record book, etc.).
     * In dark mode: selected [AppBgTheme] bg.
     * In light mode: the game-board theme's own light background so it stays coherent.
     */
    fun currentBgColor(ctx: Context): Int =
        if (isEffectivelyLight(ctx))
            currentTheme(ctx).bg
        else
            AppBgThemes.ALL[bgThemeIndex(ctx).coerceIn(0, AppBgThemes.ALL.lastIndex)].bg

    // ── Game board theme ────────────────────────────────────────────────────

    /** The resolved theme index for a game (game-specific if overridden, else global). */
    fun effectiveThemeIndex(ctx: Context, game: String? = null): Int =
        if (game != null && !PrefsManager.isGameUsingGlobalTheme(ctx, game))
            PrefsManager.getGameThemeIndex(ctx, game)
        else
            themeIndex(ctx)

    /**
     * The active [GameTheme] for the given context.
     *
     * When [Themes.SYSTEM_COLORS_INDEX] is selected and the device supports
     * dynamic color (Android 12+), a theme is generated from Material You
     * attributes. Falls back to Classic if the device doesn't support it.
     */
    fun currentTheme(ctx: Context, game: String? = null): GameTheme {
        val idx = effectiveThemeIndex(ctx, game)
        if (idx == Themes.SYSTEM_COLORS_INDEX) {
            return SystemColorTheme.generate(ctx, isEffectivelyLight(ctx))
                ?: if (isEffectivelyLight(ctx)) Themes.CLASSIC.second else Themes.CLASSIC.first
        }
        val pair = Themes.ALL[idx.coerceIn(0, Themes.ALL.lastIndex)]
        return if (isEffectivelyLight(ctx)) pair.second else pair.first
    }

    /**
     * Applies the background color to the window + decor view, and updates the
     * status/navigation bar colours and icon appearance to match the current mode.
     *
     * - game != null → game board theme bg (in-game activities)
     * - game == null → app background theme bg (shell activities)
     */
    fun applyWindowBackground(activity: Activity, game: String? = null) {
        val bg    = if (game != null) currentTheme(activity, game).bg else currentBgColor(activity)
        val light = isEffectivelyLight(activity)

        activity.window.setBackgroundDrawable(ColorDrawable(bg))
        activity.window.decorView.setBackgroundColor(bg)
        activity.window.statusBarColor     = bg
        activity.window.navigationBarColor = bg

        // Light-mode → dark status-bar icons; dark-mode → light icons.
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = light
    }
}
