package com.pocketarcade.leaderboard

import android.app.Dialog
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
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

private data class GameInfo(val key: String, val iconRes: Int, val label: String)
private val GAMES = listOf(
    GameInfo("snake",        R.drawable.ic_snake,        "SNAKE"),
    GameInfo("pong",         R.drawable.ic_pong,         "PONG"),
    GameInfo("asteroids",    R.drawable.ic_asteroids,    "ASTEROIDS"),
    GameInfo("brickbreaker", R.drawable.ic_brickbreaker, "BRICKBREAKER"),
    GameInfo("cavedriver",   R.drawable.ic_cavedriver,   "CAVE DIVER"),
    GameInfo("memorymatch",  R.drawable.ic_memorymatch,  "MEMORY")
)

private fun pongScoreColor(game: String, score: Int): Int? {
    if (game != "pong" && !game.startsWith("pong_")) return null
    val ps = when {
        score >= 80 -> score / 100
        score >= 10 -> score / 10
        else        -> 0
    }
    return if (ps >= 7) Color.parseColor("#2ecc71") else Color.parseColor("#e74c3c")
}

// ── Location abbreviation helpers ─────────────────────────────────────────────

private fun formatLocationShort(country: String, state: String): String {
    val cc = countryCode(country)
    return when {
        country == "United States" && state.isNotEmpty() ->
            "${abbreviateState(country, state)}, $cc"
        country == "Canada" && state.isNotEmpty() ->
            "${abbreviateState(country, state)}, $cc"
        cc.isNotEmpty() -> cc
        country.isNotEmpty() -> country.take(8)
        else -> ""
    }
}

private fun modeLabel(mode: String?): String = when (mode?.lowercase()) {
    "easy"   -> "(E) "
    "medium" -> "(M) "
    "hard"   -> "(H) "
    else     -> ""
}

fun showGlobalLeaderboardDialog(
    activity: AppCompatActivity,
    game: String,
    mode: String? = null,
    pendingScore: PendingGlobalScore? = null,
    initialTab: String = "WORLD",
    initialTimeRange: TimeRange = TimeRange.ALL_TIME
) {
    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_global_leaderboard, null)
    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        val screenH = activity.resources.displayMetrics.heightPixels
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, (screenH * 0.88f).toInt())
    }

    val dp          = activity.resources.displayMetrics.density
    val accentBlue  = activity.getColor(R.color.accent_blue)
    val accentGreen = activity.getColor(R.color.accent_green)
    val mutedColor  = activity.getColor(R.color.muted)

    val tvCurrentGame    = view.findViewById<TextView>(R.id.tvCurrentGame)
    val gameChipsRow     = view.findViewById<LinearLayout>(R.id.gameChipsRow)
    val modeChipsScroll  = view.findViewById<View>(R.id.modeChipsScroll)
    val modeChipsRow     = view.findViewById<LinearLayout>(R.id.modeChipsRow)
    val tabWorld         = view.findViewById<TextView>(R.id.tabWorld)
    val tabLocal         = view.findViewById<TextView>(R.id.tabLocal)
    val tabFriends       = view.findViewById<TextView>(R.id.tabFriends)
    val tabPersonal      = view.findViewById<TextView>(R.id.tabPersonal)
    val container        = view.findViewById<LinearLayout>(R.id.globalContainer)
    val progress         = view.findViewById<ProgressBar>(R.id.progressGlobal)
    val tvEmpty          = view.findViewById<TextView>(R.id.tvGlobalEmpty)
    val friendsBar       = view.findViewById<LinearLayout>(R.id.friendsBar)
    val tabFriendsWeek   = view.findViewById<TextView>(R.id.tabFriendsWeek)
    val tabFriendsMonth  = view.findViewById<TextView>(R.id.tabFriendsMonth)
    val tabFriendsAll    = view.findViewById<TextView>(R.id.tabFriendsAll)
    val btnManageFriends = view.findViewById<TextView>(R.id.btnManageFriends)
    val btnLoadMore      = view.findViewById<TextView>(R.id.btnLoadMore)

    // Dialog-level mutable state
    var currentGame      = game
    var currentMode      = mode
    var currentTab       = "WORLD"
    var friendsTimeRange = TimeRange.ALL_TIME
    var pageLastDoc: DocumentSnapshot? = null
    var loadedCount      = 0
    var myUid: String?   = GlobalLeaderboard.currentUid
    var myFollowingUids  = emptySet<String>()
    var myMutualUids     = emptySet<String>()

    val country = PrefsManager.getGlobalCountry(activity)
    val state   = PrefsManager.getGlobalState(activity)

    // Pre-load social graph so WORLD/LOCAL rows get friend highlighting
    fun loadSocialGraph() {
        val uid = myUid ?: return
        FriendsManager.getFollowing(uid) { following ->
            myFollowingUids = following.map { it.uid }.toSet()
            if (following.isNotEmpty()) {
                FriendsManager.checkMutuals(uid, following.map { it.uid }) { mutuals ->
                    myMutualUids = mutuals
                }
            }
        }
    }
    if (myUid != null) loadSocialGraph()

    // ── Chip builder ─────────────────────────────────────────────────────────

    fun makeChip(text: String, active: Boolean, iconRes: Int? = null, iconColor: Int? = null): TextView {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20 * dp
            if (active) setColor(accentBlue)
            else { setColor(Color.TRANSPARENT); setStroke((1 * dp).toInt(), mutedColor) }
        }
        return TextView(activity).apply {
            this.text = text
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setTextColor(if (active) Color.WHITE else mutedColor)
            background = bg
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            if (iconRes != null) {
                val iconSize = (16 * dp).toInt()
                val d = activity.getDrawable(iconRes)?.mutate()
                when {
                    active     -> d?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                    iconColor != null -> d?.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
                }
                d?.setBounds(0, 0, iconSize, iconSize)
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = (4 * dp).toInt()
            }
        }
    }

    // ── User profile dialog ───────────────────────────────────────────────────

    fun showUserProfileDialog(entry: GlobalEntry) {
        showPlayerProfileDialog(
            activity  = activity,
            entry     = entry,
            myUid     = myUid,
            isFollowing = entry.uid in myFollowingUids,
            onFollowChanged = { isNowFollowing ->
                if (isNowFollowing) {
                    myFollowingUids = myFollowingUids + entry.uid
                } else {
                    myFollowingUids = myFollowingUids - entry.uid
                    myMutualUids    = myMutualUids    - entry.uid
                }
            }
        )
    }

    // ── Row builder ───────────────────────────────────────────────────────────

    fun buildGlobalRow(rank: Int, entry: GlobalEntry): View {
        fun Int.px() = (this * dp).toInt()

        val isMe      = !myUid.isNullOrEmpty() && entry.uid == myUid
        val isMutual  = entry.uid in myMutualUids

        val rankColor = when (rank) {
            1    -> Color.parseColor("#FFD700")
            2    -> Color.parseColor("#C0C0C0")
            3    -> Color.parseColor("#CD7F32")
            else -> if (isMe) accentBlue else if (isMutual) accentGreen else mutedColor
        }
        val nameColor = when {
            isMe     -> accentBlue
            isMutual -> accentGreen
            else     -> Color.WHITE
        }
        // Mode prefix "(H) " etc. only when showing all-modes view
        val prefix     = if (currentMode == null) modeLabel(entry.mode) else ""
        val scoreText  = prefix + formatGlobalScore(currentGame, entry.score)
        val scoreColor = pongScoreColor(currentGame, entry.score) ?: accentBlue
        val location   = formatLocationShort(entry.country, entry.state)

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.px(), 7.px(), 4.px(), 7.px())
            isClickable = true
            isFocusable = true
            when {
                isMe -> background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 6 * dp
                    setColor(Color.argb(40, 79, 142, 247))
                }
                isMutual -> background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 6 * dp
                    setColor(Color.argb(30, 0, 204, 102))
                    setStroke(1, Color.argb(60, 0, 204, 102))
                }
            }
        }

        // Rank — 46dp, right-aligned
        row.addView(TextView(activity).apply {
            text = "#%,d".format(rank)
            setTextColor(rankColor)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(46.px(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        // Avatar — 28dp with 4dp start margin
        val avatarFrame = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(28.px(), 28.px()).apply {
                marginStart = 4.px()
            }
        }
        avatarFrame.addView(AvatarUtils.buildView(activity, entry.avatarIndex, entry.avatarColor, 22))
        row.addView(avatarFrame)

        // 4dp spacer
        row.addView(View(activity).apply { layoutParams = LinearLayout.LayoutParams(4.px(), 1) })

        // Username — weight=1, wraps to 2 lines for long names
        row.addView(TextView(activity).apply {
            text = if (isMutual) "${entry.username} ♻" else entry.username
            setTextColor(nameColor)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Score — 80dp (wide enough for 7-digit scores)
        row.addView(TextView(activity).apply {
            text = scoreText
            setTextColor(scoreColor)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(80.px(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        // Region — 88dp
        row.addView(TextView(activity).apply {
            text = location
            setTextColor(mutedColor)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(88.px(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        row.setOnClickListener { showUserProfileDialog(entry) }
        return row
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    fun appendEntries(entries: List<GlobalEntry>, newLastDoc: DocumentSnapshot?) {
        activity.runOnUiThread {
            progress.visibility = View.GONE
            if (loadedCount == 0 && entries.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = if (currentTab == "FRIENDS")
                    "Add friends to see their scores!" else "No scores yet - be the first!"
                btnLoadMore.visibility = View.GONE
                return@runOnUiThread
            }
            tvEmpty.visibility = View.GONE
            entries.forEach { entry ->
                loadedCount++
                container.addView(buildGlobalRow(loadedCount, entry))
            }
            pageLastDoc = newLastDoc
            btnLoadMore.visibility = if (newLastDoc != null) View.VISIBLE else View.GONE
        }
    }

    fun loadPersonalScores() {
        fun Int.px() = (this * dp).toInt()
        container.removeAllViews()
        tvEmpty.visibility = View.GONE
        progress.visibility = View.GONE
        btnLoadMore.visibility = View.GONE
        loadedCount = 0

        data class PersonalGame(val key: String, val label: String, val iconRes: Int, val color: Int)
        val personalGames = listOf(
            PersonalGame("snake",              "SNAKE",        R.drawable.ic_snake,        Color.parseColor("#4f8ef7")),
            PersonalGame("pong",               "PONG",         R.drawable.ic_pong,         Color.parseColor("#e74c3c")),
            PersonalGame("asteroids",          "ASTEROIDS",    R.drawable.ic_asteroids,    Color.parseColor("#00d4ff")),
            PersonalGame("brickbreaker",       "BRKR BREAKER", R.drawable.ic_brickbreaker, Color.parseColor("#f1c40f")),
            PersonalGame("cavedriver",         "CAVE DIVER",   R.drawable.ic_cavedriver,   Color.parseColor("#00FFCC")),
            PersonalGame("memorymatch_easy",   "MEMORY (E)",   R.drawable.ic_memorymatch,  Color.parseColor("#00FF96")),
            PersonalGame("memorymatch_medium", "MEMORY (M)",   R.drawable.ic_memorymatch,  Color.parseColor("#00FF96")),
            PersonalGame("memorymatch_hard",   "MEMORY (H)",   R.drawable.ic_memorymatch,  Color.parseColor("#00FF96"))
        )

        var hasAny = false
        personalGames.forEach { game ->
            val score = PrefsManager.getHighScore(activity, game.key)
            if (score <= 0) return@forEach
            hasAny = true
            val scoreStr = formatGlobalScore(game.key, score)

            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4.px(), 10.px(), 4.px(), 10.px())
            }
            val iconSize = 20.px()
            row.addView(ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                setImageResource(game.iconRes)
                setColorFilter(game.color, PorterDuff.Mode.SRC_IN)
            })
            row.addView(TextView(activity).apply {
                text = game.label
                setTextColor(game.color)
                textSize = 14f
                typeface = Typeface.MONOSPACE
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 8.px()
                }
            })
            row.addView(TextView(activity).apply {
                text = scoreStr
                setTextColor(accentBlue)
                textSize = 14f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            container.addView(row)
        }

        if (!hasAny) {
            tvEmpty.text = "No local scores yet — play a game!"
            tvEmpty.visibility = View.VISIBLE
        }
    }

    fun loadFirstPage() {
        container.removeAllViews()
        tvEmpty.visibility = View.GONE
        progress.visibility = View.VISIBLE
        btnLoadMore.visibility = View.GONE
        loadedCount = 0
        pageLastDoc = null
        when (currentTab) {
            "WORLD" -> GlobalLeaderboard.fetchGlobalPage(currentGame, currentMode) { entries, last ->
                appendEntries(entries, last)
            }
            "LOCAL" -> GlobalLeaderboard.fetchLocalPage(currentGame, country, state, currentMode) { entries, last ->
                appendEntries(entries, last)
            }
            "FRIENDS" -> GlobalLeaderboard.ensureSignedIn(
                onReady = { uid ->
                    myUid = uid
                    FriendsManager.getFollowing(uid) { following ->
                        myFollowingUids = following.map { it.uid }.toSet()
                        // Always include own UID so the user sees their own score
                        // even when they have no friends yet.
                        val allUids = (listOf(uid) + following.map { it.uid }).distinct()
                        FriendsManager.fetchFriendsScores(
                            uids = allUids,
                            game = currentGame,
                            timeRange = friendsTimeRange,
                            mode = currentMode,
                            onResult = { appendEntries(it, null) }
                        )
                    }
                },
                onError = { _ -> appendEntries(emptyList(), null) }
            )
        }
    }

    fun loadNextPage() {
        val cursor = pageLastDoc ?: return
        btnLoadMore.visibility = View.GONE
        progress.visibility = View.VISIBLE
        when (currentTab) {
            "WORLD" -> GlobalLeaderboard.fetchGlobalPage(currentGame, currentMode, after = cursor) { entries, last ->
                appendEntries(entries, last)
            }
            "LOCAL" -> GlobalLeaderboard.fetchLocalPage(currentGame, country, state, currentMode, after = cursor) { entries, last ->
                appendEntries(entries, last)
            }
        }
    }

    // ── Chip setup ────────────────────────────────────────────────────────────

    fun setupModeChips() {
        if (currentGame != "brickbreaker" && currentGame != "memorymatch") {
            modeChipsScroll.visibility = View.GONE
            return
        }
        modeChipsScroll.visibility = View.VISIBLE
        modeChipsRow.removeAllViews()
        listOf(null to "ALL", "easy" to "EASY", "medium" to "MEDIUM", "hard" to "HARD")
            .forEachIndexed { i, (key, label) ->
                val chip = makeChip(label, currentMode == key)
                if (i > 0) (chip.layoutParams as LinearLayout.LayoutParams).marginStart = (6 * dp).toInt()
                chip.setOnClickListener {
                    currentMode = key
                    setupModeChips()
                    loadFirstPage()
                }
                modeChipsRow.addView(chip)
            }
    }

    val gameChipColors = mapOf(
        "snake"        to Color.parseColor("#4f8ef7"),
        "pong"         to Color.parseColor("#e74c3c"),
        "asteroids"    to Color.parseColor("#00d4ff"),
        "brickbreaker" to Color.parseColor("#f1c40f"),
        "cavedriver"   to Color.parseColor("#00FFCC"),
        "memorymatch"  to Color.parseColor("#00FF96")
    )

    fun setupGameChips() {
        gameChipsRow.removeAllViews()
        GAMES.forEachIndexed { i, g ->
            val chip = makeChip(g.label, g.key == currentGame, g.iconRes, gameChipColors[g.key])
            if (i > 0) (chip.layoutParams as LinearLayout.LayoutParams).marginStart = (6 * dp).toInt()
            chip.setOnClickListener {
                currentGame = g.key
                currentMode = null
                tvCurrentGame.text = g.label
                setupGameChips()
                setupModeChips()
                if (currentTab != "PERSONAL") loadFirstPage()
            }
            gameChipsRow.addView(chip)
        }
    }

    // ── Tab UI helpers ────────────────────────────────────────────────────────

    fun refreshThirdTab() {
        tabFriends.text = if (PrefsManager.getGlobalUsername(activity) != null) "FRIENDS" else "REGISTER"
    }

    fun setFriendsTimeActive(range: TimeRange) {
        friendsTimeRange = range
        tabFriendsWeek.setTextColor(if (range == TimeRange.WEEK) accentBlue else mutedColor)
        tabFriendsMonth.setTextColor(if (range == TimeRange.MONTH) accentBlue else mutedColor)
        tabFriendsAll.setTextColor(if (range == TimeRange.ALL_TIME) accentBlue else mutedColor)
    }

    fun setActive(tab: String) {
        currentTab = tab
        tabWorld.setTextColor(if (tab == "WORLD") accentBlue else mutedColor)
        tabLocal.setTextColor(if (tab == "LOCAL") accentBlue else mutedColor)
        tabFriends.setTextColor(if (tab == "FRIENDS") accentBlue else mutedColor)
        tabPersonal.setTextColor(if (tab == "PERSONAL") accentBlue else mutedColor)
        friendsBar.visibility = if (tab == "FRIENDS") View.VISIBLE else View.GONE
        if (tab == "FRIENDS" || tab == "PERSONAL") btnLoadMore.visibility = View.GONE
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    if (country.isNotEmpty()) tabLocal.visibility = View.VISIBLE
    tabFriends.visibility = View.VISIBLE
    refreshThirdTab()

    val initGame = GAMES.find { it.key == currentGame }
    tvCurrentGame.text = initGame?.label ?: currentGame.uppercase()

    setupGameChips()
    setupModeChips()

    // ── Click listeners ───────────────────────────────────────────────────────

    tabWorld.setOnClickListener  { setActive("WORLD"); loadFirstPage() }
    tabLocal.setOnClickListener  { setActive("LOCAL"); loadFirstPage() }
    tabFriends.setOnClickListener {
        if (PrefsManager.getGlobalUsername(activity) != null) {
            setActive("FRIENDS"); loadFirstPage()
        } else {
            GlobalLeaderboard.ensureSignedIn(
                onReady = { uid ->
                    myUid = uid
                    activity.runOnUiThread {
                        showUsernameSetupDialog(activity, uid, pendingScore, onSuccess = {
                            refreshThirdTab()
                            setActive("FRIENDS"); loadFirstPage()
                        })
                    }
                },
                onError = { msg ->
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Sign-in failed: $msg", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    tabPersonal.setOnClickListener { setActive("PERSONAL"); loadPersonalScores() }
    tabFriendsWeek.setOnClickListener  { setFriendsTimeActive(TimeRange.WEEK);     loadFirstPage() }
    tabFriendsMonth.setOnClickListener { setFriendsTimeActive(TimeRange.MONTH);    loadFirstPage() }
    tabFriendsAll.setOnClickListener   { setFriendsTimeActive(TimeRange.ALL_TIME); loadFirstPage() }
    setFriendsTimeActive(initialTimeRange)

    btnManageFriends.setOnClickListener {
        val myUsername = PrefsManager.getGlobalUsername(activity) ?: return@setOnClickListener
        GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
            myUid = uid
            activity.runOnUiThread {
                showFriendsDialog(activity, uid, myUsername,
                    PrefsManager.getAvatarIndex(activity), PrefsManager.getAvatarColor(activity))
            }
        })
    }

    btnLoadMore.setOnClickListener { loadNextPage() }
    view.findViewById<TextView>(R.id.btnGlobalClose).setOnClickListener { dialog.dismiss() }

    setActive(initialTab)
    loadFirstPage()
    dialog.show()
}

// ── Registration prompt ───────────────────────────────────────────────────────

fun showRegistrationPromptIfNeeded(activity: AppCompatActivity) {
    if (PrefsManager.getGlobalUsername(activity) != null) return
    if (PrefsManager.isRegPromptDismissed(activity)) return

    val view = activity.layoutInflater.inflate(R.layout.dialog_registration_prompt, null)
    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    view.findViewById<TextView>(R.id.btnRegisterNow).setOnClickListener {
        dialog.dismiss()
        GlobalLeaderboard.ensureSignedIn(
            onReady = { uid ->
                activity.runOnUiThread {
                    showUsernameSetupDialog(activity, uid, pendingScore = null, onSuccess = {})
                }
            },
            onError = { msg ->
                activity.runOnUiThread {
                    Toast.makeText(activity, "Sign-in failed: $msg", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
    view.findViewById<TextView>(R.id.btnRemindLater).setOnClickListener { dialog.dismiss() }
    view.findViewById<TextView>(R.id.btnDontShowAgain).setOnClickListener {
        PrefsManager.setRegPromptDismissed(activity)
        dialog.dismiss()
    }
    dialog.show()
}

// ── Styled game picker (replaces AlertDialog.setItems) ────────────────────────

fun showGlobalLeaderboardPicker(activity: AppCompatActivity) {
    val dp = activity.resources.displayMetrics.density
    fun Int.px() = (this * dp).toInt()

    val dialog = Dialog(activity)

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

    root.addView(TextView(activity).apply {
        text = "GLOBAL LEADERBOARD"
        textSize = 15f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(activity.getColor(R.color.accent_blue))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 8.px() }
    })

    GAMES.forEach { g ->
        val tile = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = activity.getDrawable(R.drawable.bg_game_tile)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48.px()
            ).apply { topMargin = 8.px() }
        }
        val iconSize = 20.px()
        tile.addView(ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            setImageResource(g.iconRes)
        })
        tile.addView(TextView(activity).apply {
            text = g.label
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 8.px() }
        })
        tile.setOnClickListener {
            dialog.dismiss()
            showGlobalLeaderboardDialog(activity, g.key)
        }
        root.addView(tile)
    }

    root.addView(TextView(activity).apply {
        text = "CANCEL"
        setTextColor(activity.getColor(R.color.muted))
        textSize = 13f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 40.px()
        ).apply { topMargin = 12.px() }
        setOnClickListener { dialog.dismiss() }
    })

    dialog.setContentView(root)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    dialog.show()
}

// ── Username setup dialog ─────────────────────────────────────────────────────

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
