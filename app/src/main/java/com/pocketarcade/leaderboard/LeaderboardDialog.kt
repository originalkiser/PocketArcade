package com.pocketarcade.leaderboard

import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.R
import com.pocketarcade.ShareUtils
import com.pocketarcade.storage.PrefsManager

private val LETTERS = Array(27) { i -> if (i == 0) " " else ('A' + i - 1).toString() }

private fun isPongGame(game: String) = game == "pong" || game.startsWith("pong_")

private fun decodePongScore(enc: Int): String {
    val ps: Int; val ai: Int
    when {
        enc >= 80  -> { ps = enc / 100; ai = 99 - enc % 100 }
        enc >= 10  -> { ps = enc / 10;  ai = enc % 10 }
        else       -> { ps = 0;         ai = enc }
    }
    return "$ps-$ai"
}

private fun isMemoryMatchGame(game: String) = game == "memorymatch" || game.startsWith("memorymatch_")

/** Converts an encoded Memory Match score back to "X moves".
 *  If the game key includes a mode suffix (e.g. "memorymatch_easy") the threshold
 *  is exact; for the combined key ("memorymatch") the threshold is inferred from
 *  the score range (easy < 2000, medium < 7000, hard ≥ 7000). */
private fun decodeMemoryMatchScore(game: String, score: Int): String {
    val threshold = when {
        game.endsWith("_medium") -> 5000
        game.endsWith("_hard")   -> 10000
        game.endsWith("_easy")   -> 1000
        score >= 7001            -> 10000   // hard range
        score >= 2001            -> 5000    // medium range
        else                     -> 1000    // easy range
    }
    return "${threshold - score} moves"
}

fun showLeaderboardDialog(
    activity: AppCompatActivity,
    game: String,
    highlightRank: Int = -1,
    shareScore: Int = -1,
    mode: String? = null,
    onDismiss: () -> Unit = {}
) {
    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_leaderboard, null)
    val container = view.findViewById<LinearLayout>(R.id.leaderboardContainer)
    val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)

    // Show mode-specific entries when mode is supplied (e.g. "memorymatch_easy" for easy MM scores)
    val displayGame = if (mode != null) "${game}_$mode" else game
    val entries = LeaderboardManager.getEntries(activity, displayGame)

    if (entries.isEmpty()) {
        tvEmpty.visibility = View.VISIBLE
    } else {
        tvEmpty.visibility = View.GONE
        entries.forEachIndexed { index, entry ->
            val row = LayoutInflater.from(activity).inflate(R.layout.item_leaderboard, container, false)
            row.findViewById<TextView>(R.id.tvRank).text = "#${index + 1}"
            row.findViewById<TextView>(R.id.tvInitials).text = entry.initials.trim().ifEmpty { "---" }
            row.findViewById<TextView>(R.id.tvScore).text = when {
                isPongGame(game)        -> decodePongScore(entry.score)
                isMemoryMatchGame(game) -> decodeMemoryMatchScore(game, entry.score)
                else                    -> entry.score.toString()
            }
            row.findViewById<TextView>(R.id.tvDate).text = entry.formattedDate
            container.addView(row)

            if (index + 1 == highlightRank) {
                val flashAnim = ValueAnimator.ofArgb(
                    Color.parseColor("#33f1c40f"),
                    Color.TRANSPARENT
                ).apply {
                    duration = 600
                    repeatCount = 5
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { row.setBackgroundColor(it.animatedValue as Int) }
                }
                row.post { flashAnim.start() }
            }
        }
    }

    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    dialog.setOnDismissListener { onDismiss() }

    val topScore = entries.firstOrNull()?.score ?: 0
    val scoreToShare = if (shareScore >= 0) shareScore else topScore
    val scoreDate = entries.firstOrNull { it.score == scoreToShare }?.formattedDate ?: ""

    view.findViewById<TextView>(R.id.btnShare).setOnClickListener {
        ShareUtils.shareScore(activity, game, scoreToShare, scoreDate, mode)
    }
    view.findViewById<TextView>(R.id.btnWorld).setOnClickListener {
        val avatarIndex = PrefsManager.getAvatarIndex(activity)
        val avatarColor = PrefsManager.getAvatarColor(activity)
        val pending = if (shareScore >= 0) PendingGlobalScore(game, shareScore, mode, avatarIndex, avatarColor) else null
        showGlobalLeaderboardDialog(activity, game, mode, pending)
    }
    view.findViewById<TextView>(R.id.btnFriends).setOnClickListener {
        val (gGame, gMode) = toGlobalKey(game, mode)
        showGlobalLeaderboardDialog(activity, gGame, gMode,
            initialTab = "FRIENDS", initialTimeRange = TimeRange.WEEK)
    }
    view.findViewById<TextView>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
    dialog.show()
}

fun showInitialsThenLeaderboard(
    activity: AppCompatActivity,
    game: String,
    score: Int,
    mode: String? = null,
    onDone: () -> Unit = {}
) {
    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_initials, null)
    val pickerA = view.findViewById<NumberPicker>(R.id.pickerA)
    val pickerB = view.findViewById<NumberPicker>(R.id.pickerB)
    val pickerC = view.findViewById<NumberPicker>(R.id.pickerC)
    val preview = view.findViewById<TextView>(R.id.tvInitialsPreview)
    view.findViewById<TextView>(R.id.tvInitialsScore).text = when {
        isPongGame(game)        -> "SCORE: ${decodePongScore(score)}"
        isMemoryMatchGame(game) -> decodeMemoryMatchScore(game, score)
        else                    -> "SCORE: $score"
    }

    val lbSize = PrefsManager.getLeaderboardSize(activity)
    view.findViewById<TextView>(R.id.tvNewTop).text = "🏆 NEW TOP $lbSize!"

    val last = PrefsManager.getLastInitials(activity).padEnd(3)

    fun letterIndex(ch: Char): Int {
        if (ch == ' ') return 0
        val idx = ch.uppercaseChar() - 'A' + 1
        return idx.coerceIn(0, 26)
    }

    fun configPicker(p: NumberPicker, defaultChar: Char) {
        p.minValue = 0
        p.maxValue = LETTERS.size - 1
        p.displayedValues = LETTERS
        p.value = letterIndex(defaultChar)
        p.wrapSelectorWheel = true
    }
    configPicker(pickerA, last[0])
    configPicker(pickerB, last[1])
    configPicker(pickerC, last[2])

    fun updatePreview() {
        preview.text = "${LETTERS[pickerA.value]}${LETTERS[pickerB.value]}${LETTERS[pickerC.value]}"
    }
    updatePreview()

    val listener = NumberPicker.OnValueChangeListener { _, _, _ -> updatePreview() }
    pickerA.setOnValueChangedListener(listener)
    pickerB.setOnValueChangedListener(listener)
    pickerC.setOnValueChangedListener(listener)

    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.setCancelable(false)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    view.findViewById<TextView>(R.id.btnSave).setOnClickListener {
        val initials = "${LETTERS[pickerA.value]}${LETTERS[pickerB.value]}${LETTERS[pickerC.value]}"
        PrefsManager.setLastInitials(activity, initials)
        val rankCombined = LeaderboardManager.addEntry(activity, game, score, initials)
        if (mode != null) LeaderboardManager.addEntry(activity, "${game}_$mode", score, initials)
        // Resolve rank within the mode-specific leaderboard when mode is present
        val displayGame = if (mode != null) "${game}_$mode" else game
        val rank = if (mode != null) {
            val modeEntries = LeaderboardManager.getEntries(activity, displayGame)
            val idx = modeEntries.indexOfFirst { it.score == score }
            if (idx >= 0) idx + 1 else rankCombined
        } else rankCombined
        dialog.dismiss()
        val globalUsername = PrefsManager.getGlobalUsername(activity)
        if (globalUsername != null) {
            val country     = PrefsManager.getGlobalCountry(activity)
            val state       = PrefsManager.getGlobalState(activity)
            val avatarIndex = PrefsManager.getAvatarIndex(activity)
            val avatarColor = PrefsManager.getAvatarColor(activity)
            GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
                GlobalLeaderboard.submitScore(uid, globalUsername, game, score, country, state, mode, avatarIndex, avatarColor)
            })
        }
        activity.runOnUiThread {
            showLeaderboardDialog(activity, game, rank, shareScore = score, mode = mode, onDismiss = onDone)
        }
    }

    view.findViewById<TextView>(R.id.btnSkip).setOnClickListener {
        LeaderboardManager.addEntry(activity, game, score, "   ")
        if (mode != null) LeaderboardManager.addEntry(activity, "${game}_$mode", score, "   ")
        dialog.dismiss()
        activity.runOnUiThread {
            showLeaderboardDialog(activity, game, shareScore = score, mode = mode, onDismiss = onDone)
        }
    }

    view.findViewById<TextView>(R.id.btnDontAdd).setOnClickListener {
        val confirmView = LayoutInflater.from(activity).inflate(R.layout.dialog_confirm, null)
        val confirmDialog = Dialog(activity)
        confirmDialog.setContentView(confirmView)
        confirmDialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        confirmView.findViewById<TextView>(R.id.tvConfirmMessage).text =
            "Skip this score?\nIt won\'t be saved to the leaderboard."
        confirmView.findViewById<TextView>(R.id.btnConfirmNo).setOnClickListener {
            confirmDialog.dismiss()
        }
        confirmView.findViewById<TextView>(R.id.btnConfirmYes).setOnClickListener {
            confirmDialog.dismiss()
            dialog.dismiss()
            activity.runOnUiThread {
                showLeaderboardDialog(activity, game, shareScore = score, mode = mode, onDismiss = onDone)
            }
        }
        confirmDialog.show()
    }

    dialog.show()
}

fun checkAndShowLeaderboard(
    activity: AppCompatActivity,
    game: String,
    score: Int,
    mode: String? = null,
    onDone: () -> Unit = {}
) {
    // Qualify against the mode-specific leaderboard when mode is present, so that e.g.
    // an easy Memory Match score is compared only against other easy scores.
    val qualifyGame = if (mode != null) "${game}_$mode" else game
    if (LeaderboardManager.qualifies(activity, qualifyGame, score)) {
        showInitialsThenLeaderboard(activity, game, score, mode, onDone)
    } else {
        LeaderboardManager.addEntry(activity, game, score, "   ")
        if (mode != null) LeaderboardManager.addEntry(activity, "${game}_$mode", score, "   ")
        // No high score — don't auto-show the full leaderboard.
        // Present a light prompt: primary "Play Again" + secondary "View Leaderboard".
        showNoHighScorePrompt(activity, game, score, mode, onDone)
    }
}

/**
 * Shown after a game ends when the score doesn't qualify for the top list.
 * Primary action: return to game (calls [onDone] which makes the restart UI visible).
 * Secondary action: open the full local leaderboard first, then [onDone].
 */
private fun showNoHighScorePrompt(
    activity: AppCompatActivity,
    game: String,
    score: Int,
    mode: String? = null,
    onDone: () -> Unit
) {
    val dp = activity.resources.displayMetrics.density

    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
        background = GradientDrawable().apply {
            cornerRadius = 16f * dp
            setColor(activity.getColor(R.color.surface))
            setStroke((1f * dp).toInt(), activity.getColor(R.color.border))
        }
    }

    // Score label (format matches the per-game decoding already used in leaderboard rows)
    val scoreText = when {
        isPongGame(game)        -> "SCORE  ${decodePongScore(score)}"
        isMemoryMatchGame(game) -> "${decodeMemoryMatchScore(game, score)}"
        else                    -> "SCORE  $score"
    }
    root.addView(TextView(activity).apply {
        text = scoreText
        setTextColor(activity.getColor(R.color.text))
        textSize = 20f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, (18 * dp).toInt())
    })

    // Primary: PLAY AGAIN
    val btnPlay = TextView(activity).apply {
        text = "▶  PLAY AGAIN"
        setTextColor(activity.getColor(R.color.accent_blue))
        textSize = 14f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = 8f * dp
            setColor(0x224488FF.toInt())
            setStroke((1f * dp).toInt(), activity.getColor(R.color.accent_blue))
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (48 * dp).toInt()
        ).also { it.bottomMargin = (10 * dp).toInt() }
        isClickable = true; isFocusable = true
    }
    root.addView(btnPlay)

    // Secondary: VIEW LOCAL LEADERBOARD
    val btnLb = TextView(activity).apply {
        text = "🏆 View Local Leaderboard"
        setTextColor(activity.getColor(R.color.muted))
        textSize = 12f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (40 * dp).toInt()
        )
        isClickable = true; isFocusable = true
    }
    root.addView(btnLb)

    val dialog = Dialog(activity)
    dialog.setContentView(root)
    dialog.setCancelable(false)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    btnPlay.setOnClickListener { dialog.dismiss(); onDone() }
    btnLb.setOnClickListener  {
        dialog.dismiss()
        showLeaderboardDialog(activity, game, shareScore = score, mode = mode, onDismiss = onDone)
    }
    dialog.show()
}

/**
 * Normalises a local leaderboard game key to the (globalGame, globalMode) pair used
 * for Firestore queries. Handles "blockdrop_easy" → ("blockdrop", "easy") etc.
 */
private fun toGlobalKey(game: String, mode: String?): Pair<String, String?> {
    if (mode != null) return game to mode
    val known = setOf("snake", "pong", "asteroids", "brickbreaker", "cavedriver", "blockdrop", "memorymatch")
    val parts  = game.split("_", limit = 2)
    return if (parts.size == 2 && parts[0] in known) parts[0] to parts[1] else game to null
}

/**
 * Called from each game activity after the local leaderboard is dismissed.
 *
 * Checks whether the score qualifies for auto-navigation to:
 *  1. **Global leaderboard** — score lands in top-100 worldwide for game+mode.
 *  2. **Friends leaderboard** — score equals the user's stored period-best for WEEK, MONTH,
 *     or ALL_TIME (meaning it was accepted as their new best for that period).
 *
 * Sequence: global first (with pulse/scroll), then friends on dismiss (with pulse/scroll).
 * Only one dialog chain is ever started; if neither qualifies, nothing is shown.
 *
 * @param globalGame  Base Firestore game key (e.g. "blockdrop", not "blockdrop_easy")
 * @param globalMode  Mode string or null
 * @param score       Raw score just submitted
 */
fun handlePostGameLeaderboards(
    activity: AppCompatActivity,
    globalGame: String,
    globalMode: String?,
    score: Int
) {
    PrefsManager.getGlobalUsername(activity) ?: return   // not registered → no auto-nav

    GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
        var globalDone    = false
        var friendsDone   = false
        var onGlobalBoard = false
        var bestRange: TimeRange? = null

        fun navigate() {
            if (!globalDone || !friendsDone) return
            if (activity.isFinishing || activity.isDestroyed) return
            when {
                onGlobalBoard && bestRange != null -> showGlobalLeaderboardDialog(
                    activity    = activity,
                    game        = globalGame,
                    mode        = globalMode,
                    pulseUid    = uid,
                    onDismissed = {
                        if (!activity.isFinishing && !activity.isDestroyed)
                            showGlobalLeaderboardDialog(
                                activity         = activity,
                                game             = globalGame,
                                mode             = globalMode,
                                pulseUid         = uid,
                                initialTab       = "FRIENDS",
                                initialTimeRange = bestRange!!
                            )
                    }
                )
                onGlobalBoard -> showGlobalLeaderboardDialog(
                    activity = activity,
                    game     = globalGame,
                    mode     = globalMode,
                    pulseUid = uid
                )
                bestRange != null -> showGlobalLeaderboardDialog(
                    activity         = activity,
                    game             = globalGame,
                    mode             = globalMode,
                    pulseUid         = uid,
                    initialTab       = "FRIENDS",
                    initialTimeRange = bestRange!!
                )
                // else: neither qualifies — return to game, no auto-navigation
            }
        }

        // ── 1. Check global rank (top 100) ──────────────────────────────────────
        // Only auto-navigate if the score is a new personal best (not just any
        // qualifying score — the user may already have a higher entry on the board).
        GlobalLeaderboard.fetchGlobal(globalGame, globalMode, limit = 100L) { entries ->
            val cutoff = entries.lastOrNull()?.score ?: 0
            val inTop100 = entries.isNotEmpty() && (entries.size < 100 || score >= cutoff)
            val myExisting = entries.find { it.uid == uid }
            // Qualify only when the score is genuinely new/improved for this player
            onGlobalBoard = inTop100 && (myExisting == null || score >= myExisting.score)
            globalDone = true
            navigate()
        }

        // ── 2. Check friends rank for WEEK, MONTH, ALL_TIME ─────────────────────
        FriendsManager.getFollowing(uid) { following ->
            val allUids = (listOf(uid) + following.map { it.uid }).distinct()
            var pending = 3
            val qualifying = mutableSetOf<TimeRange>()

            fun finishFriends() {
                bestRange = listOf(TimeRange.WEEK, TimeRange.MONTH, TimeRange.ALL_TIME)
                    .firstOrNull { it in qualifying }
                friendsDone = true
                navigate()
            }

            listOf(TimeRange.WEEK, TimeRange.MONTH, TimeRange.ALL_TIME).forEach { range ->
                FriendsManager.fetchFriendsScores(allUids, globalGame, range, globalMode) { entries ->
                    // Qualifies when the stored period-best equals the just-submitted score,
                    // meaning the server accepted it as a new personal best for this period.
                    val myScore = entries.find { it.uid == uid }?.score
                    if (myScore != null && myScore == score) qualifying.add(range)
                    if (--pending == 0) finishFriends()
                }
            }
        }
    })
}
