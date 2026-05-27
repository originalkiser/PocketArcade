package com.pocketarcade.storage

import android.content.Context
import androidx.core.content.edit

object PrefsManager {
    private const val PREFS = "pocket_arcade_prefs"

    private const val KEY_SCORE_PREFIX = "hi_score_"
    private const val KEY_AD_FREE = "ad_free"
    private const val KEY_SESSION_COUNT = "session_count"
    private const val KEY_SESSIONS_UNTIL_UPSELL = "sessions_until_upsell"
    private const val KEY_DEMO_MODE = "demo_mode"
    private const val KEY_SOUND = "sound"
    private const val KEY_THEME_INDEX = "theme_index"
    private const val KEY_LIGHT_MODE = "light_mode"

    const val GAME_SNAKE = "snake"
    const val GAME_PONG = "pong"
    const val GAME_ASTEROIDS = "asteroids"
    const val GAME_BRICKBREAKER = "brickbreaker"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getHighScore(ctx: Context, game: String): Int =
        prefs(ctx).getInt(KEY_SCORE_PREFIX + game, 0)

    fun setHighScore(ctx: Context, game: String, score: Int) {
        val current = getHighScore(ctx, game)
        if (score > current) prefs(ctx).edit { putInt(KEY_SCORE_PREFIX + game, score) }
    }

    fun isAdFree(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AD_FREE, false)

    fun setAdFree(ctx: Context, value: Boolean) = prefs(ctx).edit { putBoolean(KEY_AD_FREE, value) }

    fun isDemoModeEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DEMO_MODE, true)

    fun setDemoModeEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit { putBoolean(KEY_DEMO_MODE, value) }

    fun isSoundEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SOUND, true)

    fun setSoundEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit { putBoolean(KEY_SOUND, value) }

    /** Increment session count and return true if upsell should be shown. */
    fun onSessionStart(ctx: Context): Boolean {
        if (isAdFree(ctx)) return false
        val p = prefs(ctx)
        val count = p.getInt(KEY_SESSION_COUNT, 0) + 1
        var until = p.getInt(KEY_SESSIONS_UNTIL_UPSELL, nextUpsellInterval())
        p.edit {
            putInt(KEY_SESSION_COUNT, count)
        }
        if (count >= until) {
            p.edit { putInt(KEY_SESSIONS_UNTIL_UPSELL, count + nextUpsellInterval()) }
            return true
        }
        return false
    }

    fun resetHighScores(ctx: Context) {
        prefs(ctx).edit {
            remove(KEY_SCORE_PREFIX + GAME_SNAKE)
            remove(KEY_SCORE_PREFIX + GAME_PONG)
            remove(KEY_SCORE_PREFIX + GAME_ASTEROIDS)
            remove(KEY_SCORE_PREFIX + GAME_BRICKBREAKER)
        }
    }

    fun getThemeIndex(ctx: Context): Int = prefs(ctx).getInt(KEY_THEME_INDEX, 0)
    fun setThemeIndex(ctx: Context, index: Int) = prefs(ctx).edit { putInt(KEY_THEME_INDEX, index) }

    fun isLightMode(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_LIGHT_MODE, false)
    fun setGlobalLightMode(ctx: Context, value: Boolean) = prefs(ctx).edit { putBoolean(KEY_LIGHT_MODE, value) }

    // Per-game theme overrides
    fun getGameThemeIndex(ctx: Context, game: String): Int =
        prefs(ctx).getInt("game_theme_$game", getThemeIndex(ctx))
    fun setGameThemeIndex(ctx: Context, game: String, index: Int) =
        prefs(ctx).edit { putInt("game_theme_$game", index) }
    fun isGameUsingGlobalTheme(ctx: Context, game: String): Boolean =
        prefs(ctx).getBoolean("game_global_$game", true)
    fun setGameUsingGlobalTheme(ctx: Context, game: String, value: Boolean) =
        prefs(ctx).edit { putBoolean("game_global_$game", value) }

    private fun nextUpsellInterval() = (3..5).random()
}
