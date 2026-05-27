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

    /** The resolved theme index for a game (game-specific if overridden, else global). */
    fun effectiveThemeIndex(ctx: Context, game: String? = null): Int =
        if (game != null && !PrefsManager.isGameUsingGlobalTheme(ctx, game))
            PrefsManager.getGameThemeIndex(ctx, game)
        else
            themeIndex(ctx)

    /** Sets window and root layout background to match the current theme. */
    fun applyWindowBackground(activity: Activity, game: String? = null) {
        val bg = currentTheme(activity, game).bg
        activity.window.setBackgroundDrawable(ColorDrawable(bg))
        activity.window.decorView.setBackgroundColor(bg)
    }

    /** The active GameTheme for a game. Pass game = null for the global theme. */
    fun currentTheme(ctx: Context, game: String? = null): GameTheme {
        val pair = Themes.ALL[effectiveThemeIndex(ctx, game).coerceIn(0, Themes.ALL.lastIndex)]
        return if (isLightMode(ctx)) pair.second else pair.first
    }
}
