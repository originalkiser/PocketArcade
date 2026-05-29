package com.pocketarcade.leaderboard

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.R
import com.pocketarcade.storage.PrefsManager

data class PendingGlobalScore(val game: String, val score: Int, val mode: String?)

fun showGlobalLeaderboardDialog(
    activity: AppCompatActivity,
    game: String,
    mode: String? = null
) {
    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_global_leaderboard, null)
    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    val tabWorld   = view.findViewById<TextView>(R.id.tabWorld)
    val tabLocal   = view.findViewById<TextView>(R.id.tabLocal)
    val container  = view.findViewById<LinearLayout>(R.id.globalContainer)
    val progress   = view.findViewById<ProgressBar>(R.id.progressGlobal)
    val tvEmpty    = view.findViewById<TextView>(R.id.tvGlobalEmpty)

    val country = PrefsManager.getGlobalCountry(activity)
    val state   = PrefsManager.getGlobalState(activity)
    if (country.isNotEmpty()) tabLocal.visibility = View.VISIBLE

    val accentBlue = activity.getColor(R.color.accent_blue)
    val muted      = activity.getColor(R.color.muted)

    fun setActive(tab: String) {
        tabWorld.setTextColor(if (tab == "WORLD") accentBlue else muted)
        tabLocal.setTextColor(if (tab == "LOCAL") accentBlue else muted)
    }

    fun populate(entries: List<GlobalEntry>) {
        activity.runOnUiThread {
            progress.visibility = View.GONE
            container.removeAllViews()
            if (entries.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
            } else {
                tvEmpty.visibility = View.GONE
                entries.forEachIndexed { i, e ->
                    container.addView(buildGlobalRow(activity, i + 1, e, game))
                }
            }
        }
    }

    fun load(tab: String) {
        container.removeAllViews()
        tvEmpty.visibility = View.GONE
        progress.visibility = View.VISIBLE
        if (tab == "WORLD") {
            GlobalLeaderboard.fetchGlobal(game, mode, onResult = ::populate)
        } else {
            GlobalLeaderboard.fetchLocal(game, country, state, mode, onResult = ::populate)
        }
    }

    tabWorld.setOnClickListener { setActive("WORLD"); load("WORLD") }
    tabLocal.setOnClickListener { setActive("LOCAL"); load("LOCAL") }

    setActive("WORLD")
    load("WORLD")

    view.findViewById<TextView>(R.id.btnGlobalClose).setOnClickListener { dialog.dismiss() }
    dialog.show()
}

private fun buildGlobalRow(
    activity: AppCompatActivity,
    rank: Int,
    entry: GlobalEntry,
    game: String
): View {
    val dp = activity.resources.displayMetrics.density
    fun Int.px() = (this * dp).toInt()

    val accentBlue = Color.parseColor("#4f8ef7")
    val muted      = Color.parseColor("#666688")

    val scoreText  = formatGlobalScore(game, entry.score)
    val scoreColor = pongScoreColor(game, entry.score) ?: accentBlue
    val location   = if (entry.state.isNotEmpty()) "${entry.state}, ${entry.country}" else entry.country

    val row = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(4.px(), 8.px(), 4.px(), 8.px())
    }

    fun tv(text: String, color: Int, widthDp: Int = -1, weight: Float = 0f, grav: Int = Gravity.START, sizeSp: Float = 14f) =
        TextView(activity).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            gravity = grav
            typeface = Typeface.MONOSPACE
            layoutParams = if (widthDp >= 0)
                LinearLayout.LayoutParams(widthDp.px(), LinearLayout.LayoutParams.WRAP_CONTENT)
            else
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        }

    row.addView(tv("#$rank",    accentBlue, widthDp = 28, grav = Gravity.CENTER))
    row.addView(tv(entry.username, Color.WHITE, weight = 1f))
    row.addView(tv(scoreText,   scoreColor,  widthDp = 52, grav = Gravity.END))
    row.addView(tv(location,    muted,       widthDp = 88, grav = Gravity.END, sizeSp = 11f))
    return row
}

private fun formatGlobalScore(game: String, score: Int): String {
    if (game != PrefsManager.GAME_PONG && !game.startsWith("pong_")) return score.toString()
    val ps: Int; val ai: Int
    when {
        score >= 80 -> { ps = score / 100; ai = 99 - score % 100 }
        score >= 10 -> { ps = score / 10;  ai = score % 10 }
        else        -> { ps = 0;            ai = score }
    }
    return "$ps-$ai"
}

private fun pongScoreColor(game: String, score: Int): Int? {
    if (game != PrefsManager.GAME_PONG && !game.startsWith("pong_")) return null
    val ps = when {
        score >= 80 -> score / 100
        score >= 10 -> score / 10
        else        -> 0
    }
    return if (ps >= 7) Color.parseColor("#2ecc71") else Color.parseColor("#e74c3c")
}

fun showUsernameSetupDialog(
    activity: AppCompatActivity,
    uid: String,
    pendingScore: PendingGlobalScore? = null,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_username_setup, null)
    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.setCancelable(true)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    val etUsername     = view.findViewById<EditText>(R.id.etUsername)
    val tvError        = view.findViewById<TextView>(R.id.tvUsernameError)
    val spinnerCountry = view.findViewById<Spinner>(R.id.spinnerCountry)
    val layoutState    = view.findViewById<View>(R.id.layoutState)
    val spinnerState   = view.findViewById<Spinner>(R.id.spinnerState)
    val btnClaim       = view.findViewById<TextView>(R.id.btnClaim)
    val btnLater       = view.findViewById<TextView>(R.id.btnLater)

    val countryAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, LocationData.countries)
    countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinnerCountry.adapter = countryAdapter
    spinnerCountry.setSelection(LocationData.countries.indexOf("United States").coerceAtLeast(0))

    fun updateStateSpinner() {
        val country = LocationData.countries[spinnerCountry.selectedItemPosition]
        val regions = LocationData.subregions(country)
        layoutState.visibility = if (regions.isNotEmpty()) View.VISIBLE else View.GONE
        if (regions.isNotEmpty()) {
            val sa = ArrayAdapter(activity, android.R.layout.simple_spinner_item, regions)
            sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerState.adapter = sa
        }
    }
    updateStateSpinner()
    spinnerCountry.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = updateStateSpinner()
        override fun onNothingSelected(p: AdapterView<*>?) {}
    }

    val usernameRegex = Regex("^[a-z0-9_]{3,20}$")

    btnClaim.setOnClickListener {
        tvError.visibility = View.GONE
        val username = etUsername.text.toString().lowercase().trim()
        if (!usernameRegex.matches(username)) {
            tvError.text = "3-20 chars: letters, numbers, underscore only"
            tvError.visibility = View.VISIBLE
            return@setOnClickListener
        }
        val country = LocationData.countries[spinnerCountry.selectedItemPosition]
        val state   = if (layoutState.visibility == View.VISIBLE)
            LocationData.subregions(country)[spinnerState.selectedItemPosition] else ""

        btnClaim.isEnabled = false
        tvError.text = "Checking availability..."
        tvError.visibility = View.VISIBLE

        GlobalLeaderboard.claimUsername(
            username  = username,
            uid       = uid,
            country   = country,
            state     = state,
            onSuccess = {
                PrefsManager.setGlobalUsername(activity, username)
                PrefsManager.setGlobalCountry(activity, country)
                PrefsManager.setGlobalState(activity, state)
                pendingScore?.let { ps ->
                    GlobalLeaderboard.submitScore(uid, username, ps.game, ps.score, country, state, ps.mode)
                }
                dialog.dismiss()
                activity.runOnUiThread { onSuccess() }
            },
            onTaken = {
                activity.runOnUiThread {
                    btnClaim.isEnabled = true
                    tvError.text = "Username taken - try another"
                    tvError.visibility = View.VISIBLE
                }
            },
            onError = {
                activity.runOnUiThread {
                    btnClaim.isEnabled = true
                    tvError.text = "Error - check your connection"
                    tvError.visibility = View.VISIBLE
                }
            }
        )
    }

    btnLater.setOnClickListener { dialog.dismiss() }
    dialog.setOnDismissListener { onDismiss() }
    dialog.show()
}
