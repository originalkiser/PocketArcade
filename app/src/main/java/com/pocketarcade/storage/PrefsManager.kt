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
    private const val KEY_THEME_INDEX    = "theme_index"
    private const val KEY_BG_THEME_INDEX = "bg_theme_index"
    private const val KEY_LIGHT_MODE     = "light_mode"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_LAST_INITIALS = "last_initials"
    private const val KEY_LEADERBOARD_SIZE = "leaderboard_size"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_BB_DIFFICULTY   = "bb_difficulty"
    private const val KEY_GLOBAL_USERNAME  = "global_username"
    private const val KEY_GLOBAL_COUNTRY   = "global_country"
    private const val KEY_GLOBAL_STATE     = "global_state"
    private const val KEY_AVATAR_INDEX          = "avatar_index"
    private const val KEY_AVATAR_COLOR          = "avatar_color"
    private const val KEY_REG_PROMPT_DISMISSED  = "reg_prompt_dismissed"
    private const val KEY_PROFILE_PHOTO_PATH    = "profile_photo_path"
    private const val KEY_HINT_PREFIX           = "hint_shown_"
    private const val KEY_GAMES_PLAYED = "games_played"

    const val HINT_FRIENDS        = "friends"
    const val HINT_AUTOFIRE       = "autofire"
    const val HINT_SNAKE_CONTROLS = "snake_controls"
    private const val KEY_LAST_UPSELL_AT = "last_upsell_at_game"

    const val GAME_SNAKE = "snake"
    const val GAME_PONG = "pong"
    const val GAME_ASTEROIDS = "asteroids"
    const val GAME_BRICKBREAKER = "brickbreaker"

    // ── Stats ─────────────────────────────────────────────────────────────────
    private fun statKey(metric: String, game: String, mode: String? = null) =
        if (mode != null) "stat_${metric}_${game}_$mode" else "stat_${metric}_$game"

    fun recordGameStat(ctx: Context, game: String, score: Int, durationMs: Long, mode: String? = null) {
        prefs(ctx).edit {
            putInt(statKey("plays", game),
                prefs(ctx).getInt(statKey("plays", game), 0) + 1)
            putLong(statKey("score_total", game),
                prefs(ctx).getLong(statKey("score_total", game), 0L) + score)
            putLong(statKey("time_ms", game),
                prefs(ctx).getLong(statKey("time_ms", game), 0L) + durationMs)
            if (mode != null) {
                putInt(statKey("plays", game, mode),
                    prefs(ctx).getInt(statKey("plays", game, mode), 0) + 1)
                putLong(statKey("score_total", game, mode),
                    prefs(ctx).getLong(statKey("score_total", game, mode), 0L) + score)
                putLong(statKey("time_ms", game, mode),
                    prefs(ctx).getLong(statKey("time_ms", game, mode), 0L) + durationMs)
            }
        }
    }

    fun getStatPlays(ctx: Context, game: String, mode: String? = null): Int =
        prefs(ctx).getInt(statKey("plays", game, mode), 0)

    fun getStatTotalScore(ctx: Context, game: String, mode: String? = null): Long =
        prefs(ctx).getLong(statKey("score_total", game, mode), 0L)

    fun getStatTotalTimeMs(ctx: Context, game: String, mode: String? = null): Long =
        prefs(ctx).getLong(statKey("time_ms", game, mode), 0L)

    fun addSnakeFruits(ctx: Context, count: Int) {
        val key = "stat_snake_fruits"
        prefs(ctx).edit { putLong(key, prefs(ctx).getLong(key, 0L) + count) }
    }

    fun getSnakeFruits(ctx: Context): Long =
        prefs(ctx).getLong("stat_snake_fruits", 0L)

    fun recordPongWin(ctx: Context) {
        val key = "stat_pong_wins"
        prefs(ctx).edit { putInt(key, prefs(ctx).getInt(key, 0) + 1) }
    }

    fun getPongWins(ctx: Context): Int =
        prefs(ctx).getInt("stat_pong_wins", 0)

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

    fun getBgThemeIndex(ctx: Context): Int = prefs(ctx).getInt(KEY_BG_THEME_INDEX, 0)
    fun setBgThemeIndex(ctx: Context, index: Int) = prefs(ctx).edit { putInt(KEY_BG_THEME_INDEX, index) }

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

    fun getGlobalUsername(ctx: Context): String? = prefs(ctx).getString(KEY_GLOBAL_USERNAME, null)
    fun setGlobalUsername(ctx: Context, v: String) = prefs(ctx).edit { putString(KEY_GLOBAL_USERNAME, v) }
    fun getGlobalCountry(ctx: Context): String = prefs(ctx).getString(KEY_GLOBAL_COUNTRY, "") ?: ""
    fun setGlobalCountry(ctx: Context, v: String) = prefs(ctx).edit { putString(KEY_GLOBAL_COUNTRY, v) }
    fun getGlobalState(ctx: Context): String = prefs(ctx).getString(KEY_GLOBAL_STATE, "") ?: ""
    fun setGlobalState(ctx: Context, v: String) = prefs(ctx).edit { putString(KEY_GLOBAL_STATE, v) }
    fun getAvatarIndex(ctx: Context): Int = prefs(ctx).getInt(KEY_AVATAR_INDEX, 0)
    fun setAvatarIndex(ctx: Context, v: Int) = prefs(ctx).edit { putInt(KEY_AVATAR_INDEX, v) }
    fun getAvatarColor(ctx: Context): Int = prefs(ctx).getInt(KEY_AVATAR_COLOR, 0)
    fun setAvatarColor(ctx: Context, v: Int) = prefs(ctx).edit { putInt(KEY_AVATAR_COLOR, v) }

    fun isRegPromptDismissed(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_REG_PROMPT_DISMISSED, false)
    fun setRegPromptDismissed(ctx: Context) =
        prefs(ctx).edit { putBoolean(KEY_REG_PROMPT_DISMISSED, true) }

    fun getProfilePhotoPath(ctx: Context): String? = prefs(ctx).getString(KEY_PROFILE_PHOTO_PATH, null)
    fun setProfilePhotoPath(ctx: Context, path: String?) = prefs(ctx).edit {
        if (path != null) putString(KEY_PROFILE_PHOTO_PATH, path) else remove(KEY_PROFILE_PHOTO_PATH)
    }

    fun isHintShown(ctx: Context, key: String): Boolean =
        prefs(ctx).getBoolean(KEY_HINT_PREFIX + key, false)
    fun markHintShown(ctx: Context, key: String) =
        prefs(ctx).edit { putBoolean(KEY_HINT_PREFIX + key, true) }

    // ── Auto-fire preference (Asteroids) ──────────────────────────────────────
    fun isAutoFireEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("autofire_enabled", true)
    fun setAutoFireEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit { putBoolean("autofire_enabled", value) }

    // ── Snake control-hint tracking ───────────────────────────────────────────
    /** Number of times the "try other control mode" hint has been shown (capped at 2). */
    fun getSnakeControlHintCount(ctx: Context): Int =
        prefs(ctx).getInt("snake_hint_count", 0)
    fun setSnakeControlHintCount(ctx: Context, count: Int) =
        prefs(ctx).edit { putInt("snake_hint_count", count) }

    /** One-time migration: reset any time_ms value that is absurdly large (> 10 days),
     *  which would be a sign it was inflated by demo-mode sessions before the fix in v5. */
    fun migrateInflatedTime(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean("time_migration_v1", false)) return
        val tenDaysMs = 10L * 24 * 60 * 60 * 1000
        val games = listOf(GAME_SNAKE, GAME_PONG, GAME_ASTEROIDS, GAME_BRICKBREAKER)
        p.edit {
            for (game in games) {
                val key = statKey("time_ms", game)
                if (p.getLong(key, 0L) > tenDaysMs) putLong(key, 0L)
            }
            putBoolean("time_migration_v1", true)
        }
    }

    private fun nextUpsellInterval() = (3..5).random()

    fun recordGamePlayed(ctx: Context) {
        val count = prefs(ctx).getInt(KEY_GAMES_PLAYED, 0) + 1
        prefs(ctx).edit { putInt(KEY_GAMES_PLAYED, count) }
    }

    /** Returns true (once) every 3rd game played, never on first launch. */
    fun checkAndConsumeUpsellTrigger(ctx: Context): Boolean {
        if (isAdFree(ctx)) return false
        val count = prefs(ctx).getInt(KEY_GAMES_PLAYED, 0)
        if (count == 0) return false
        val lastShown = prefs(ctx).getInt(KEY_LAST_UPSELL_AT, 0)
        if (count % 3 == 0 && count != lastShown) {
            prefs(ctx).edit { putInt(KEY_LAST_UPSELL_AT, count) }
            return true
        }
        return false
    }
}
