package com.pocketarcade

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.pocketarcade.games.asteroids.AsteroidsActivity
import com.pocketarcade.games.brickbreaker.BrickBreakerActivity
import com.pocketarcade.games.pong.PongActivity
import com.pocketarcade.games.snake.SnakeActivity
import com.pocketarcade.leaderboard.FriendsManager
import com.pocketarcade.leaderboard.FollowEntry
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.leaderboard.currentMonthKey
import com.pocketarcade.leaderboard.currentWeekKey
import com.pocketarcade.leaderboard.formatGlobalScore
import com.pocketarcade.storage.PrefsManager

// ── Data model ────────────────────────────────────────────────────────────────

data class DisplacementEvent(
    val friendUid: String,
    val friendUsername: String,
    val game: String,
    val periodType: String,  // "week" | "month" | "alltime"
    val periodKey: String,   // e.g. "2026-W23" | "2026-06" | "alltime"
    val friendScore: Int,
    val myScore: Int
) {
    /** Unique ID used to track which events have already been shown. */
    val eventId: String get() = "$friendUid|$game|$periodType|$periodKey"

    val periodLabel: String get() = when (periodType) {
        "week"    -> "This Week"
        "month"   -> "This Month"
        "alltime" -> "All Time"
        else      -> periodType
    }

    val gameLabel: String get() = when (game) {
        "snake"        -> "Snake"
        "pong"         -> "Pong"
        "asteroids"    -> "Asteroids"
        "brickbreaker" -> "Brick Breaker"
        else           -> game
    }
}

// ── Manager ───────────────────────────────────────────────────────────────────

/**
 * On-open displacement check: silently queries period + all-time leaderboard scores for
 * the current user and their friends, then shows a single in-app popup listing every
 * friend who has overtaken the user since the last check.
 *
 * Shown at most once per process lifetime (ranThisSession) and throttled to once per
 * hour across launches.  Each event is identified by a stable ID so it is never
 * re-shown after being dismissed.
 */
object DisplacementManager {

    private const val THROTTLE_MS = 60 * 60 * 1000L  // 1 hour between checks

    @Volatile private var ranThisSession = false

    private val ALL_GAMES = listOf(
        PrefsManager.GAME_SNAKE,
        PrefsManager.GAME_PONG,
        PrefsManager.GAME_ASTEROIDS,
        PrefsManager.GAME_BRICKBREAKER
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call from MainActivity after the changelog / onboarding sequence completes.
     * Idempotent within one process lifetime; silently no-ops if unregistered or throttled.
     */
    fun checkAndShowIfNeeded(activity: AppCompatActivity) {
        if (ranThisSession) return
        if (PrefsManager.getGlobalUsername(activity) == null) return

        val now = System.currentTimeMillis()
        if (now - PrefsManager.getLastDisplacementCheck(activity) < THROTTLE_MS) {
            ranThisSession = true
            return
        }

        val myUid = GlobalLeaderboard.currentUid
        if (myUid == null) {
            GlobalLeaderboard.ensureSignedIn(onReady = { uid -> doCheck(activity, uid, now) })
            return
        }
        doCheck(activity, myUid, now)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun doCheck(activity: AppCompatActivity, myUid: String, now: Long) {
        ranThisSession = true
        PrefsManager.setLastDisplacementCheck(activity, now)

        FriendsManager.getFollowing(myUid) { following ->
            if (following.isEmpty()) return@getFollowing

            val friendUids     = following.map { it.uid }
            val allUids        = (listOf(myUid) + friendUids).distinct()
            val friendUidToName = following.associate { it.uid to it.username }

            val weekKey  = currentWeekKey()
            val monthKey = currentMonthKey()

            // ── Collect results from 4 parallel async fetches ──────────────────
            var myAllTime:    Map<String, Int>?          = null  // game → my best
            var friendAllTime: Map<String, FriendEntry>? = null  // "uid|game" → best
            var weekScores:   Map<String, Int>?          = null  // "uid|game" → best
            var monthScores:  Map<String, Int>?          = null

            fun tryBuild() {
                val mat = myAllTime     ?: return
                val fat = friendAllTime ?: return
                val ws  = weekScores    ?: return
                val ms  = monthScores   ?: return

                val events = mutableListOf<DisplacementEvent>()

                // All-time
                for (game in ALL_GAMES) {
                    val myBest = mat[game] ?: 0
                    if (myBest <= 0) continue
                    for (friend in following) {
                        val entry = fat["${friend.uid}|$game"] ?: continue
                        if (entry.score > myBest) {
                            events += DisplacementEvent(
                                friend.uid, entry.username, game,
                                "alltime", "alltime", entry.score, myBest
                            )
                        }
                    }
                }

                // Week
                buildPeriodEvents(myUid, ws, following, "week", weekKey, events)

                // Month
                buildPeriodEvents(myUid, ms, following, "month", monthKey, events)

                val dismissed = PrefsManager.getDismissedDisplacementIds(activity)
                val newEvents = events
                    .filter { it.eventId !in dismissed }
                    .distinctBy { it.eventId }  // deduplicate within this run

                if (newEvents.isEmpty()) return

                activity.runOnUiThread { showPopup(activity, newEvents) }
            }

            // Fire all fetches in parallel (all callbacks land on the main thread)
            GlobalLeaderboard.fetchUserBestScores(myUid) { scores ->
                myAllTime = scores; tryBuild()
            }
            fetchFriendAllTimeScores(friendUids) { scores ->
                friendAllTime = scores; tryBuild()
            }
            FriendsManager.fetchAllPeriodScores(allUids, "week", weekKey) { scores ->
                weekScores = scores; tryBuild()
            }
            FriendsManager.fetchAllPeriodScores(allUids, "month", monthKey) { scores ->
                monthScores = scores; tryBuild()
            }
        }
    }

    private fun buildPeriodEvents(
        myUid: String,
        scores: Map<String, Int>,
        friends: List<FollowEntry>,
        periodType: String,
        periodKey: String,
        into: MutableList<DisplacementEvent>
    ) {
        for (game in ALL_GAMES) {
            val myScore = scores["$myUid|$game"] ?: 0
            if (myScore <= 0) continue
            for (friend in friends) {
                val friendScore = scores["${friend.uid}|$game"] ?: 0
                if (friendScore > myScore) {
                    into += DisplacementEvent(
                        friend.uid, friend.username, game,
                        periodType, periodKey, friendScore, myScore
                    )
                }
            }
        }
    }

    // ── Firestore helper ──────────────────────────────────────────────────────

    private data class FriendEntry(val username: String, val score: Int)

    private fun fetchFriendAllTimeScores(
        friendUids: List<String>,
        onResult: (Map<String, FriendEntry>) -> Unit
    ) {
        if (friendUids.isEmpty()) { onResult(emptyMap()); return }
        FirebaseFirestore.getInstance()
            .collection("globalScores")
            .whereIn("uid", friendUids.take(30))
            .get()
            .addOnSuccessListener { snap ->
                val result = mutableMapOf<String, FriendEntry>()
                snap.documents.forEach { doc ->
                    val uid      = doc.getString("uid")      ?: return@forEach
                    val game     = doc.getString("game")     ?: return@forEach
                    val score    = (doc.getLong("score") ?: 0L).toInt()
                    val username = doc.getString("username") ?: "???"
                    val key      = "$uid|$game"
                    if (score > (result[key]?.score ?: 0)) result[key] = FriendEntry(username, score)
                }
                onResult(result)
            }
            .addOnFailureListener { onResult(emptyMap()) }
    }

    // ── Popup UI ──────────────────────────────────────────────────────────────

    private fun showPopup(activity: AppCompatActivity, events: List<DisplacementEvent>) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dp = activity.resources.displayMetrics.density
        fun Int.px() = (this * dp).toInt()

        val accentRed  = activity.getColor(R.color.accent_red)
        val accentBlue = activity.getColor(R.color.accent_blue)
        val mutedColor = activity.getColor(R.color.muted)

        val dialog = Dialog(activity)

        // ── Root container ────────────────────────────────────────────────────
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.px(), 20.px(), 20.px(), 20.px())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * dp
                setColor(activity.getColor(R.color.surface))
                setStroke((1 * dp).toInt(), activity.getColor(R.color.border))
            }
        }

        // ── Header ────────────────────────────────────────────────────────────
        root.addView(TextView(activity).apply {
            text = "🏆  YOU'VE BEEN OVERTAKEN"
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(accentRed)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.px() }
        })

        // ── Scrollable event list ─────────────────────────────────────────────
        val listContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        events.forEachIndexed { i, event ->
            if (i > 0) {
                listContainer.addView(View(activity).apply {
                    setBackgroundColor(Color.argb(40, 128, 128, 128))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                    ).apply { topMargin = 8.px(); bottomMargin = 8.px() }
                })
            }

            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(4.px(), 4.px(), 4.px(), 4.px())
            }

            // e.g. "Alex passed you in Snake — This Week!"
            row.addView(TextView(activity).apply {
                text = "${event.friendUsername} passed you in ${event.gameLabel} — ${event.periodLabel}!"
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Score comparison line
            val theirFmt = formatGlobalScore(event.game, event.friendScore)
            val myFmt    = formatGlobalScore(event.game, event.myScore)
            row.addView(TextView(activity).apply {
                text = "Their $theirFmt  ·  Yours $myFmt"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(mutedColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2.px(); bottomMargin = 6.px() }
            })

            // "Jump In" button
            row.addView(TextView(activity).apply {
                text = "▶  JUMP IN"
                textSize = 12f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 6 * dp
                    setColor(accentBlue)
                }
                setPadding(12.px(), 6.px(), 12.px(), 6.px())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 36.px()
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    PrefsManager.addDismissedDisplacementIds(activity, events.map { it.eventId })
                    dialog.dismiss()
                    gameIntent(activity, event.game)?.let { activity.startActivity(it) }
                }
            })

            listContainer.addView(row)
        }

        val scrollView = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(listContainer)
        }
        root.addView(scrollView)

        // Cap scroll view height after layout
        scrollView.post {
            val maxPx = (280 * dp).toInt()
            if (scrollView.height > maxPx) {
                scrollView.layoutParams = scrollView.layoutParams.also { it.height = maxPx }
            }
        }

        // ── Dismiss All button ────────────────────────────────────────────────
        root.addView(TextView(activity).apply {
            text = "DISMISS ALL"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 40.px()
            ).apply { topMargin = 12.px() }
            setOnClickListener {
                PrefsManager.addDismissedDisplacementIds(activity, events.map { it.eventId })
                dialog.dismiss()
            }
        })

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        try { dialog.show() } catch (_: Exception) {}
    }

    private fun gameIntent(context: Context, game: String): Intent? = when (game) {
        PrefsManager.GAME_SNAKE        -> Intent(context, SnakeActivity::class.java)
        PrefsManager.GAME_PONG         -> Intent(context, PongActivity::class.java)
        PrefsManager.GAME_ASTEROIDS    -> Intent(context, AsteroidsActivity::class.java)
        PrefsManager.GAME_BRICKBREAKER -> Intent(context, BrickBreakerActivity::class.java)
        else -> null
    }
}
