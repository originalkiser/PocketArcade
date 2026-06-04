package com.pocketarcade

import android.content.Context
import com.pocketarcade.leaderboard.FriendsManager
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.storage.PrefsManager

/**
 * Silent background sync: ensures locally-recorded best scores reach Firestore even
 * if the original submission was skipped (device offline, user not yet registered,
 * or an auth error at game-over time).
 *
 * Two passes on every cold launch (guarded to run at most once per process):
 *  1. All-time: re-submits each game's device high score to globalScores.
 *     submitScore is idempotent — it only writes when the new score beats the existing record.
 *  2. Period (week/month): for each (game, modeKey), reads the locally cached best score and
 *     its timestamp. Submits only to periods whose window still contains that timestamp;
 *     scores earned in a past period are silently discarded rather than pushed to the wrong bucket.
 */
object ScoreSyncManager {

    /** Maps every game to the mode keys it can produce. "_" means no-mode (Snake, Asteroids). */
    private val GAME_MODES = mapOf(
        PrefsManager.GAME_SNAKE        to listOf("_"),
        PrefsManager.GAME_ASTEROIDS    to listOf("_"),
        PrefsManager.GAME_PONG         to listOf("easy", "medium", "hard"),
        PrefsManager.GAME_BRICKBREAKER to listOf("easy", "medium", "hard")
    )

    @Volatile private var ranThisSession = false

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call from MainActivity.onResume() — safe to call multiple times; runs at most
     * once per process lifetime.  Silent fire-and-forget; never blocks the UI thread.
     */
    fun syncOnLaunch(context: Context) {
        if (ranThisSession) return
        ranThisSession = true
        val username = PrefsManager.getGlobalUsername(context) ?: return
        GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
            syncAllTime(context, uid, username)
            syncPeriods(context, uid, username)
        })
    }

    /**
     * Call from each game activity on every game-over to keep the local cache fresh.
     * [modeKey] is "_" for Snake/Asteroids; the difficulty name (lowercase) for Pong/BB.
     */
    fun recordGameScore(context: Context, game: String, modeKey: String, score: Int) {
        PrefsManager.updateLocalPeriodBest(context, game, modeKey, score)
    }

    // ── All-time ──────────────────────────────────────────────────────────────

    private fun syncAllTime(context: Context, uid: String, username: String) {
        val country     = PrefsManager.getGlobalCountry(context)
        val state       = PrefsManager.getGlobalState(context)
        val avatarIndex = PrefsManager.getAvatarIndex(context)
        val avatarColor = PrefsManager.getAvatarColor(context)

        for ((game, _) in GAME_MODES) {
            val score = PrefsManager.getHighScore(context, game)
            if (score <= 0) continue
            // Submit with mode=null so the score appears in the "ALL modes" view.
            // Per-mode records were (or will be) written at game-over time with the actual mode.
            GlobalLeaderboard.submitScore(
                uid, username, game, score,
                country, state, null, avatarIndex, avatarColor
            )
        }
    }

    // ── Period (week / month) ─────────────────────────────────────────────────

    private fun syncPeriods(context: Context, uid: String, username: String) {
        val weekKey    = FriendsManager.currentWeekKey()
        val monthKey   = FriendsManager.currentMonthKey()
        val weekStart  = FriendsManager.calendarStartOfWeek()
        val monthStart = FriendsManager.calendarStartOfMonth()

        val country     = PrefsManager.getGlobalCountry(context)
        val state       = PrefsManager.getGlobalState(context)
        val avatarIndex = PrefsManager.getAvatarIndex(context)
        val avatarColor = PrefsManager.getAvatarColor(context)

        for ((game, modes) in GAME_MODES) {
            for (modeKey in modes) {
                val (score, ts) = PrefsManager.getLocalPeriodBest(context, game, modeKey)
                if (score <= 0 || ts <= 0L) continue
                val mode = if (modeKey == "_") null else modeKey

                // Submit only to periods whose window still covers the score's timestamp.
                // This prevents a score earned last week from polluting the current week bucket.
                if (ts >= weekStart) {
                    GlobalLeaderboard.submitPeriodScoreForPeriod(
                        uid, username, game, score, country, state,
                        mode, avatarIndex, avatarColor, "week", weekKey
                    )
                }
                if (ts >= monthStart) {
                    GlobalLeaderboard.submitPeriodScoreForPeriod(
                        uid, username, game, score, country, state,
                        mode, avatarIndex, avatarColor, "month", monthKey
                    )
                }
            }
        }
    }
}
