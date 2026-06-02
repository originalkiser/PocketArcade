package com.pocketarcade

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.pocketarcade.leaderboard.FriendsManager
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.leaderboard.formatGlobalScore
import com.pocketarcade.storage.PrefsManager
import org.json.JSONObject

/**
 * On-open "nudge" system for friend leaderboard activity.
 *
 * Strategy — snapshot comparison (no Firestore timestamp filter needed):
 *  1. Fetch all current globalScores entries for friends (one doc per player per game+mode).
 *  2. Compare against the locally-stored snapshot from the previous check.
 *  3. Any score that increased → "new high score" or "they just passed you" notification.
 *  4. Store the new snapshot for the next comparison.
 *
 * The very first check just stores the baseline and fires nothing (avoids a notification
 * flood for existing friends on first install / first login).
 */
object FriendNudgeManager {

    private const val CHANNEL_ID   = "friend_activity"
    private const val NOTIF_BEATEN = 9001
    private const val NOTIF_NEW    = 9002
    /** Permission request code — consumed in SettingsActivity.onRequestPermissionsResult. */
    const val REQ_NOTIF_PERM = 7142

    /** Minimum interval between auto-checks (4 h). */
    private const val THROTTLE_MS = 4 * 60 * 60 * 1000L

    /** Prevents re-checking multiple times in the same process lifetime before the throttle window expires. */
    @Volatile private var ranThisSession = false

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call from MainActivity.onResume().
     * Silently runs the check if the user is registered, has friends, has at least one
     * notification type enabled, and the 4-hour throttle has elapsed.
     * Never asks for the POST_NOTIFICATIONS permission here — that happens in Settings.
     */
    fun checkOnOpen(context: Context) {
        val myUid = GlobalLeaderboard.currentUid ?: return
        if (PrefsManager.getGlobalUsername(context) == null) return
        if (!PrefsManager.isNotifNewScore(context) && !PrefsManager.isNotifBeaten(context)) return
        if (!hasNotifPermission(context)) return

        val now = System.currentTimeMillis()
        val lastCheck = PrefsManager.getLastNudgeCheck(context)
        if (ranThisSession && now - lastCheck < THROTTLE_MS) return
        ranThisSession = true

        ensureChannel(context)
        runCheck(context, myUid, now, lastCheck)
    }

    /** True if we can post notifications on this device/API level. */
    fun hasNotifPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        else true

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun runCheck(context: Context, myUid: String, now: Long, lastCheck: Long) {
        FriendsManager.getFollowing(myUid) { following ->
            if (following.isEmpty()) return@getFollowing
            val friendUids = following.map { it.uid }

            val wantNew    = PrefsManager.isNotifNewScore(context)
            val wantBeaten = PrefsManager.isNotifBeaten(context)

            // Fetch user's own best scores and friends' current scores in parallel.
            // Both Firestore callbacks land on the main thread, so no sync primitives needed.
            var myScores: Map<String, Int>? = null
            var friendCurrent: Map<String, FriendEntry>? = null  // key = "uid|game|mode"

            fun onBothReady() {
                val mine   = myScores     ?: return
                val theirs = friendCurrent ?: return

                // Save new snapshot regardless of whether we notify
                val prevSnapshot = loadSnapshot(context)
                storeSnapshot(context, theirs)
                PrefsManager.setLastNudgeCheck(context, now)

                // Very first check: just establish baseline, fire nothing
                if (lastCheck == 0L) return

                val beatenLines   = mutableListOf<String>()
                val newScoreLines = mutableListOf<String>()

                for ((key, entry) in theirs) {
                    val prevScore = prevSnapshot[key] ?: 0
                    if (entry.score <= prevScore) continue        // no improvement

                    val parts     = key.split("|", limit = 3)
                    val game      = if (parts.size > 1) parts[1] else ""
                    val mode      = if (parts.size > 2) parts[2] else ""
                    val myBest    = mine[game] ?: 0
                    val label     = gameLabel(game, mode)
                    val scored    = formatGlobalScore(game, entry.score)

                    val friendJustPassedMe =
                        wantBeaten &&
                        myBest > 0 &&
                        prevScore <= myBest &&       // they were behind (or tied) before
                        entry.score > myBest         // now they're ahead

                    if (friendJustPassedMe) {
                        val mine2 = formatGlobalScore(game, myBest)
                        beatenLines.add("${entry.username} passed you in $label! ($scored vs $mine2)")
                    } else if (wantNew) {
                        newScoreLines.add("${entry.username}: $label · $scored")
                    }
                }

                if (beatenLines.isNotEmpty()) {
                    val title = if (beatenLines.size == 1) "You've been overtaken! 🏆"
                                else "${beatenLines.size} friends just passed you!"
                    post(context, NOTIF_BEATEN, title, beatenLines.take(3).joinToString("\n"))
                }
                if (newScoreLines.isNotEmpty()) {
                    val title = if (newScoreLines.size == 1) "Friend Activity 🎮"
                                else "${newScoreLines.size} friends set new high scores!"
                    post(context, NOTIF_NEW, title, newScoreLines.take(3).joinToString("\n"))
                }
            }

            GlobalLeaderboard.fetchUserBestScores(myUid) { scores ->
                myScores = scores
                onBothReady()
            }
            fetchFriendScores(friendUids) { scores ->
                friendCurrent = scores
                onBothReady()
            }
        }
    }

    // ── Snapshot persistence ──────────────────────────────────────────────────

    private data class FriendEntry(val username: String, val score: Int)

    /** Load the snapshot saved during the previous check. Returns map of "uid|game|mode" → score. */
    private fun loadSnapshot(context: Context): Map<String, Int> {
        val raw = PrefsManager.getNudgeSnapshot(context) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap { json.keys().forEach { k -> put(k, json.getInt(k)) } }
        } catch (_: Exception) { emptyMap() }
    }

    /** Persist the current friend scores so the next check can diff against them. */
    private fun storeSnapshot(context: Context, scores: Map<String, FriendEntry>) {
        val json = JSONObject()
        scores.forEach { (k, v) -> json.put(k, v.score) }
        PrefsManager.setNudgeSnapshot(context, json.toString())
    }

    // ── Firestore query ───────────────────────────────────────────────────────

    /**
     * Fetches all globalScores entries for the given UIDs and returns a map of
     * "uid|game|mode" → FriendEntry (best score per that key, which is already
     * enforced as 1-per-player by submitScore, but we guard anyway).
     */
    private fun fetchFriendScores(
        friendUids: List<String>,
        onResult: (Map<String, FriendEntry>) -> Unit
    ) {
        FirebaseFirestore.getInstance()
            .collection("globalScores")
            .whereIn("uid", friendUids.take(30))
            .get()
            .addOnSuccessListener { snap ->
                val result = mutableMapOf<String, FriendEntry>()
                snap.documents.forEach { doc ->
                    val uid   = doc.getString("uid")      ?: return@forEach
                    val game  = doc.getString("game")     ?: return@forEach
                    val score = (doc.getLong("score") ?: 0L).toInt()
                    val name  = doc.getString("username") ?: "???"
                    val mode  = doc.getString("mode")     ?: ""
                    val key   = "$uid|$game|$mode"
                    if (score > (result[key]?.score ?: 0)) result[key] = FriendEntry(name, score)
                }
                onResult(result)
            }
            .addOnFailureListener { onResult(emptyMap()) }
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun post(context: Context, id: Int, title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Friend Activity",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description =
                        "Alerts when friends post new high scores or pass your ranking"
                }
            )
        }
    }

    private fun gameLabel(game: String, mode: String): String {
        val base = when (game) {
            "snake"        -> "Snake"
            "pong"         -> "Pong"
            "asteroids"    -> "Asteroids"
            "brickbreaker" -> "Brick Breaker"
            else           -> game.replaceFirstChar { it.uppercaseChar() }
        }
        return if (mode.isNotEmpty()) "$base (${mode.replaceFirstChar { it.uppercaseChar() }})"
               else base
    }
}
