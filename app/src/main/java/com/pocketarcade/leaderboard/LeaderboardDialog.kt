package com.pocketarcade.leaderboard

import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.Color
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

    val entries = LeaderboardManager.getEntries(activity, game)

    if (entries.isEmpty()) {
        tvEmpty.visibility = View.VISIBLE
    } else {
        tvEmpty.visibility = View.GONE
        entries.forEachIndexed { index, entry ->
            val row = LayoutInflater.from(activity).inflate(R.layout.item_leaderboard, container, false)
            row.findViewById<TextView>(R.id.tvRank).text = "#${index + 1}"
            row.findViewById<TextView>(R.id.tvInitials).text = entry.initials.trim().ifEmpty { "---" }
            row.findViewById<TextView>(R.id.tvScore).text =
                if (isPongGame(game)) decodePongScore(entry.score) else entry.score.toString()
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
        val username = PrefsManager.getGlobalUsername(activity)
        if (username != null) {
            showGlobalLeaderboardDialog(activity, game, mode)
        } else {
            GlobalLeaderboard.ensureSignedIn(
                onReady = { uid ->
                    activity.runOnUiThread {
                        val avatarIndex = PrefsManager.getAvatarIndex(activity)
                        val avatarColor = PrefsManager.getAvatarColor(activity)
                        val pending = if (shareScore >= 0) PendingGlobalScore(game, shareScore, mode, avatarIndex, avatarColor) else null
                        showUsernameSetupDialog(activity, uid, pending, onSuccess = {
                            showGlobalLeaderboardDialog(activity, game, mode)
                        })
                    }
                },
                onError = {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "No connection - try again", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
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
    view.findViewById<TextView>(R.id.tvInitialsScore).text =
        "SCORE: ${if (isPongGame(game)) decodePongScore(score) else score}"

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
        val rank = LeaderboardManager.addEntry(activity, game, score, initials)
        if (mode != null) LeaderboardManager.addEntry(activity, "${game}_$mode", score, initials)
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
    if (LeaderboardManager.qualifies(activity, game, score)) {
        showInitialsThenLeaderboard(activity, game, score, mode, onDone)
    } else {
        LeaderboardManager.addEntry(activity, game, score, "   ")
        if (mode != null) LeaderboardManager.addEntry(activity, "${game}_$mode", score, "   ")
        showLeaderboardDialog(activity, game, shareScore = score, mode = mode, onDismiss = onDone)
    }
}
