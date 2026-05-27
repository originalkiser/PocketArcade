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
import com.pocketarcade.storage.PrefsManager

private val LETTERS = Array(27) { i -> if (i == 0) " " else ('A' + i - 1).toString() }

fun showLeaderboardDialog(
    activity: AppCompatActivity,
    game: String,
    highlightRank: Int = -1,
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
            row.findViewById<TextView>(R.id.tvScore).text = entry.score.toString()
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

    view.findViewById<TextView>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
    dialog.show()
}

fun showInitialsThenLeaderboard(
    activity: AppCompatActivity,
    game: String,
    score: Int,
    onDone: () -> Unit = {}
) {
    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_initials, null)
    val pickerA = view.findViewById<NumberPicker>(R.id.pickerA)
    val pickerB = view.findViewById<NumberPicker>(R.id.pickerB)
    val pickerC = view.findViewById<NumberPicker>(R.id.pickerC)
    val preview = view.findViewById<TextView>(R.id.tvInitialsPreview)
    view.findViewById<TextView>(R.id.tvInitialsScore).text = "SCORE: $score"

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
        dialog.dismiss()
        activity.runOnUiThread { showLeaderboardDialog(activity, game, rank, onDismiss = onDone) }
    }
    view.findViewById<TextView>(R.id.btnSkip).setOnClickListener {
        LeaderboardManager.addEntry(activity, game, score, "   ")
        dialog.dismiss()
        activity.runOnUiThread { showLeaderboardDialog(activity, game, onDismiss = onDone) }
    }

    dialog.show()
}

fun checkAndShowLeaderboard(
    activity: AppCompatActivity,
    game: String,
    score: Int,
    onDone: () -> Unit = {}
) {
    if (LeaderboardManager.isTopTen(activity, game, score)) {
        showInitialsThenLeaderboard(activity, game, score, onDone)
    } else {
        LeaderboardManager.addEntry(activity, game, score, "   ")
        showLeaderboardDialog(activity, game, onDismiss = onDone)
    }
}
