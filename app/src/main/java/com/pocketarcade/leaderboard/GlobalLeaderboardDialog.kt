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
import com.pocketarcade.AvatarUtils
import com.pocketarcade.R
import com.pocketarcade.storage.PrefsManager

data class PendingGlobalScore(
    val game: String,
    val score: Int,
    val mode: String?,
    val avatarIndex: Int = 0,
    val avatarColor: Int = 0
)

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

    val tabWorld        = view.findViewById<TextView>(R.id.tabWorld)
    val tabLocal        = view.findViewById<TextView>(R.id.tabLocal)
    val tabFriends      = view.findViewById<TextView>(R.id.tabFriends)
    val container       = view.findViewById<LinearLayout>(R.id.globalContainer)
    val progress        = view.findViewById<ProgressBar>(R.id.progressGlobal)
    val tvEmpty         = view.findViewById<TextView>(R.id.tvGlobalEmpty)
    val friendsBar      = view.findViewById<LinearLayout>(R.id.friendsBar)
    val tabFriendsWeek  = view.findViewById<TextView>(R.id.tabFriendsWeek)
    val tabFriendsMonth = view.findViewById<TextView>(R.id.tabFriendsMonth)
    val tabFriendsAll   = view.findViewById<TextView>(R.id.tabFriendsAll)
    val btnManageFriends = view.findViewById<TextView>(R.id.btnManageFriends)

    val country = PrefsManager.getGlobalCountry(activity)
    val state   = PrefsManager.getGlobalState(activity)
    if (country.isNotEmpty()) tabLocal.visibility = View.VISIBLE
    if (PrefsManager.getGlobalUsername(activity) != null) tabFriends.visibility = View.VISIBLE

    val accentBlue = activity.getColor(R.color.accent_blue)
    val muted      = activity.getColor(R.color.muted)

    var currentTab       = "WORLD"
    var friendsTimeRange = TimeRange.ALL_TIME

    fun setFriendsTimeActive(range: TimeRange) {
        friendsTimeRange = range
        tabFriendsWeek.setTextColor(if (range == TimeRange.WEEK) accentBlue else muted)
        tabFriendsMonth.setTextColor(if (range == TimeRange.MONTH) accentBlue else muted)
        tabFriendsAll.setTextColor(if (range == TimeRange.ALL_TIME) accentBlue else muted)
    }

    fun setActive(tab: String) {
        currentTab = tab
        tabWorld.setTextColor(if (tab == "WORLD") accentBlue else muted)
        tabLocal.setTextColor(if (tab == "LOCAL") accentBlue else muted)
        tabFriends.setTextColor(if (tab == "FRIENDS") accentBlue else muted)
        friendsBar.visibility = if (tab == "FRIENDS") View.VISIBLE else View.GONE
    }

    fun populate(entries: List<GlobalEntry>, showAvatar: Boolean = false) {
        activity.runOnUiThread {
            progress.visibility = View.GONE
            container.removeAllViews()
            if (entries.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = if (currentTab == "FRIENDS")
                    "Add friends to see their scores!" else "No scores yet - be the first!"
            } else {
                tvEmpty.visibility = View.GONE
                entries.forEachIndexed { i, e ->
                    container.addView(buildGlobalRow(activity, i + 1, e, game, showAvatar))
                }
            }
        }
    }

    fun load(tab: String) {
        container.removeAllViews()
        tvEmpty.visibility = View.GONE
        progress.visibility = View.VISIBLE
        when (tab) {
            "WORLD"   -> GlobalLeaderboard.fetchGlobal(game, mode, onResult = { populate(it) })
            "LOCAL"   -> GlobalLeaderboard.fetchLocal(game, country, state, mode, onResult = { populate(it) })
            "FRIENDS" -> {
                GlobalLeaderboard.ensureSignedIn(
                    onReady = { myUid ->
                        FriendsManager.getFollowing(myUid) { following ->
                            if (following.isEmpty()) { populate(emptyList(), showAvatar = true); return@getFollowing }
                            FriendsManager.fetchFriendsScores(
                                uids = following.map { it.uid },
                                game = game,
                                timeRange = friendsTimeRange,
                                mode = mode,
                                onResult = { populate(it, showAvatar = true) }
                            )
                        }
                    },
                    onError = { populate(emptyList()) }
                )
            }
        }
    }

    tabWorld.setOnClickListener { setActive("WORLD"); load("WORLD") }
    tabLocal.setOnClickListener { setActive("LOCAL"); load("LOCAL") }
    tabFriends.setOnClickListener { setActive("FRIENDS"); load("FRIENDS") }

    tabFriendsWeek.setOnClickListener  { setFriendsTimeActive(TimeRange.WEEK);     load("FRIENDS") }
    tabFriendsMonth.setOnClickListener { setFriendsTimeActive(TimeRange.MONTH);    load("FRIENDS") }
    tabFriendsAll.setOnClickListener   { setFriendsTimeActive(TimeRange.ALL_TIME); load("FRIENDS") }
    setFriendsTimeActive(TimeRange.ALL_TIME)

    btnManageFriends.setOnClickListener {
        val myUsername = PrefsManager.getGlobalUsername(activity) ?: return@setOnClickListener
        GlobalLeaderboard.ensureSignedIn { myUid ->
            activity.runOnUiThread {
                showFriendsDialog(activity, myUid, myUsername,
                    PrefsManager.getAvatarIndex(activity), PrefsManager.getAvatarColor(activity))
            }
        }
    }

    setActive("WORLD")
    load("WORLD")

    view.findViewById<TextView>(R.id.btnGlobalClose).setOnClickListener { dialog.dismiss() }
    dialog.show()
}

private fun buildGlobalRow(
    activity: AppCompatActivity,
    rank: Int,
    entry: GlobalEntry,
    game: String,
    showAvatar: Boolean = false
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
        setPadding(4.px(), 7.px(), 4.px(), 7.px())
    }

    fun tv(text: String, color: Int, widthDp: Int = -1, weight: Float = 0f,
           grav: Int = Gravity.START, sizeSp: Float = 14f) =
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

    row.addView(tv("#$rank", accentBlue, widthDp = 28, grav = Gravity.CENTER))

    val userRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    if (showAvatar) {
        userRow.addView(AvatarUtils.buildView(activity, entry.avatarIndex, entry.avatarColor, 20))
        userRow.addView(View(activity).apply { layoutParams = LinearLayout.LayoutParams(4.px(), 1) })
    }
    val nameTV = TextView(activity).apply {
        text = entry.username
        setTextColor(Color.WHITE)
        textSize = if (showAvatar) 13f else 14f
        typeface = Typeface.MONOSPACE
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    userRow.addView(nameTV)
    row.addView(userRow)

    row.addView(tv(scoreText, scoreColor, widthDp = 52, grav = Gravity.END))
    row.addView(tv(location,  muted,      widthDp = 88, grav = Gravity.END, sizeSp = 10f))
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
    var selAvatarEmoji = PrefsManager.getAvatarIndex(activity)
    var selAvatarColor = PrefsManager.getAvatarColor(activity)

    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_username_setup, null)
    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.setCancelable(true)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    val avatarContainer = view.findViewById<FrameLayout>(R.id.avatarPreviewSetup)
    val btnChangeAvatar = view.findViewById<TextView>(R.id.btnChangeAvatar)
    val etUsername      = view.findViewById<EditText>(R.id.etUsername)
    val tvError         = view.findViewById<TextView>(R.id.tvUsernameError)
    val spinnerCountry  = view.findViewById<Spinner>(R.id.spinnerCountry)
    val layoutState     = view.findViewById<View>(R.id.layoutState)
    val spinnerState    = view.findViewById<Spinner>(R.id.spinnerState)
    val btnClaim        = view.findViewById<TextView>(R.id.btnClaim)
    val btnLater        = view.findViewById<TextView>(R.id.btnLater)

    fun refreshAvatar() {
        avatarContainer.removeAllViews()
        avatarContainer.addView(AvatarUtils.buildView(activity, selAvatarEmoji, selAvatarColor, 52))
    }
    refreshAvatar()

    btnChangeAvatar.setOnClickListener {
        AvatarUtils.showAvatarPickerDialog(activity, selAvatarEmoji, selAvatarColor) { emojiIdx, colorIdx ->
            selAvatarEmoji = emojiIdx
            selAvatarColor = colorIdx
            refreshAvatar()
        }
    }

    val countryAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, LocationData.countries)
    countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinnerCountry.adapter = countryAdapter
    spinnerCountry.setSelection(LocationData.countries.indexOf("United States").coerceAtLeast(0))

    fun updateStateSpinner() {
        val c = LocationData.countries[spinnerCountry.selectedItemPosition]
        val regions = LocationData.subregions(c)
        layoutState.visibility = if (regions.isNotEmpty()) View.VISIBLE else View.GONE
        if (regions.isNotEmpty()) {
            spinnerState.adapter = ArrayAdapter(
                activity, android.R.layout.simple_spinner_item, regions
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
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
        val chosenCountry = LocationData.countries[spinnerCountry.selectedItemPosition]
        val chosenState   = if (layoutState.visibility == View.VISIBLE)
            LocationData.subregions(chosenCountry)[spinnerState.selectedItemPosition] else ""

        btnClaim.isEnabled = false
        tvError.text = "Checking availability..."
        tvError.visibility = View.VISIBLE

        GlobalLeaderboard.claimUsername(
            username    = username,
            uid         = uid,
            country     = chosenCountry,
            state       = chosenState,
            avatarIndex = selAvatarEmoji,
            avatarColor = selAvatarColor,
            onSuccess = {
                PrefsManager.setGlobalUsername(activity, username)
                PrefsManager.setGlobalCountry(activity, chosenCountry)
                PrefsManager.setGlobalState(activity, chosenState)
                PrefsManager.setAvatarIndex(activity, selAvatarEmoji)
                PrefsManager.setAvatarColor(activity, selAvatarColor)
                pendingScore?.let { ps ->
                    GlobalLeaderboard.submitScore(
                        uid, username, ps.game, ps.score,
                        chosenCountry, chosenState, ps.mode,
                        selAvatarEmoji, selAvatarColor
                    )
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
