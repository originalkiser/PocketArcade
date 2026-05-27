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
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_LAST_INITIALS = "last_initials"
    private const val KEY_LEADERBOARD_SIZE = "leaderboard_size"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_BB_DIFFICULTY = "bb_difficulty"

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

    fun getLastInitials(ctx: Context): String =
        prefs(ctx).getString(KEY_LAST_INITIALS, "AAA") ?: "AAA"

    fun setLastInitials(ctx: Context, initials: String) =
        prefs(ctx).edit { putString(KEY_LAST_INITIALS, initials.take(3).padEnd(3)) }

    fun getLastUpdateCheck(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LAST_UPDATE_CHECK, 0L)

    fun setLastUpdateCheck(ctx: Context, time: Long) =
        prefs(ctx).edit { putLong(KEY_LAST_UPDATE_CHECK, time) }

    fun getLeaderboardSize(ctx: Context): Int =
        prefs(ctx).getInt(KEY_LEADERBOARD_SIZE, 5)

    fun setLeaderboardSize(ctx: Context, size: Int) =
        prefs(ctx).edit { putInt(KEY_LEADERBOARD_SIZE, size.coerceIn(1, 15)) }

    fun getLastVersionCode(ctx: Context): Int = prefs(ctx).getInt(KEY_LAST_VERSION_CODE, -1)
    fun setLastVersionCode(ctx: Context, code: Int) = prefs(ctx).edit { putInt(KEY_LAST_VERSION_CODE, code) }

    // 0 = Easy, 1 = Medium, 2 = Hard
    fun getBBDifficulty(ctx: Context): Int = prefs(ctx).getInt(KEY_BB_DIFFICULTY, 1)
    fun setBBDifficulty(ctx: Context, value: Int) = prefs(ctx).edit { putInt(KEY_BB_DIFFICULTY, value.coerceIn(0, 2)) }

    private fun nextUpsellInterval() = (3..5).random()
}
