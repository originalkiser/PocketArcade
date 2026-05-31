package com.pocketarcade

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import com.pocketarcade.storage.PrefsManager

object ThemeManager {

    fun themeIndex(ctx: Context): Int = PrefsManager.getThemeIndex(ctx)
    fun isLightMode(ctx: Context): Boolean = PrefsManager.isLightMode(ctx)

    fun setThemeIndex(ctx: Context, index: Int) = PrefsManager.setThemeIndex(ctx, index)
    fun setLightMode(ctx: Context, value: Boolean) = PrefsManager.setGlobalLightMode(ctx, value)

    // ── App background theme ────────────────────────────────────────────────

    fun bgThemeIndex(ctx: Context): Int = PrefsManager.getBgThemeIndex(ctx)
    fun setBgThemeIndex(ctx: Context, index: Int) = PrefsManager.setBgThemeIndex(ctx, index)

    /**
     * Background color for shell activities (menus, settings, record book, etc.).
     * In dark mode this uses the selected [AppBgTheme]; in light mode it reuses the
     * game-board theme's own light background so it stays coherent.
     */
    fun currentBgColor(ctx: Context): Int =
        if (isLightMode(ctx))
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
     * Sets the window + decor background.
     * - game != null → game board theme bg (for in-game activities)
     * - game == null → app background theme bg (for shell activities)
     */
    fun applyWindowBackground(activity: Activity, game: String? = null) {
        val bg = if (game != null) currentTheme(activity, game).bg else currentBgColor(activity)
        activity.window.setBackgroundDrawable(ColorDrawable(bg))
        activity.window.decorView.setBackgroundColor(bg)
    }

    /** The active GameTheme for a game. Pass game = null for the global theme. */
    fun currentTheme(ctx: Context, game: String? = null): GameTheme {
        val pair = Themes.ALL[effectiveThemeIndex(ctx, game).coerceIn(0, Themes.ALL.lastIndex)]
        return if (isLightMode(ctx)) pair.second else pair.first
    }
}
