package com.pocketarcade

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pocketarcade.leaderboard.*
import com.pocketarcade.storage.PrefsManager

class FriendsActivity : AppCompatActivity() {

    private var myUid: String? = null
    private var following = listOf<FollowEntry>()
    private var mutuals = setOf<String>()
    private var myFollowingUids = setOf<String>()
    private var groupScores = mapOf<String, Map<String, Int>>()
    private var ranks = mapOf<String, Map<String, Int>>()
    private var selectedGame = PrefsManager.GAME_SNAKE
    private var selectedTimeRange = TimeRange.ALL_TIME
    private var leaderboardLoaded = false

    private data class GameInfo(val key: String, val iconRes: Int, val label: String)
    private val games = listOf(
        GameInfo(PrefsManager.GAME_SNAKE,        R.drawable.ic_snake,        "SNAKE"),
        GameInfo(PrefsManager.GAME_PONG,         R.drawable.ic_pong,         "PONG"),
        GameInfo(PrefsManager.GAME_ASTEROIDS,    R.drawable.ic_asteroids,    "ASTEROIDS"),
        GameInfo(PrefsManager.GAME_BRICKBREAKER, R.drawable.ic_brickbreaker, "BRKR BREAKER"),
        GameInfo(PrefsManager.GAME_CAVEDRIVER,   R.drawable.ic_cavedriver,   "CAVE DIVER"),
        GameInfo("blockdrop",                    R.drawable.ic_blockpop,     "BLOCK DROP"),
        GameInfo("memorymatch",                  R.drawable.ic_memorymatch,  "MEMORY")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)

        val btnBack       = findViewById<TextView>(R.id.btnFriendsBack)
        val tabMyFriends  = findViewById<TextView>(R.id.tabMyFriends)
        val tabLeaderboard = findViewById<TextView>(R.id.tabLeaderboard)
        val panelMyFriends  = findViewById<ScrollView>(R.id.panelMyFriends)
        val panelLeaderboard = findViewById<LinearLayout>(R.id.panelLeaderboard)

        btnBack.setOnClickListener { finish() }

        fun setTab(friendsActive: Boolean) {
            panelMyFriends.visibility  = if (friendsActive) View.VISIBLE else View.GONE
            panelLeaderboard.visibility = if (!friendsActive) View.VISIBLE else View.GONE
            tabMyFriends.setTextColor(if (friendsActive) getColor(R.color.accent_blue) else getColor(R.color.muted))
            tabLeaderboard.setTextColor(if (!friendsActive) getColor(R.color.accent_blue) else getColor(R.color.muted))
            if (!friendsActive && !leaderboardLoaded) {
                leaderboardLoaded = true
                loadLeaderboard()
            }
        }

        tabMyFriends.setOnClickListener { setTab(true) }
        tabLeaderboard.setOnClickListener { setTab(false) }

        setupMyProfileSection()
        setupAddFriendButton()

        if (PrefsManager.getGlobalUsername(this) != null) {
            loadData()
        }
    }

    // ── Own profile row ───────────────────────────────────────────────────────

    private fun setupMyProfileSection() {
        val isRegistered = PrefsManager.getGlobalUsername(this) != null
        val layoutNotRegistered = findViewById<LinearLayout>(R.id.layoutNotRegistered)
        val layoutFriendsContent = findViewById<LinearLayout>(R.id.layoutFriendsContent)
        val myProfileAvatar = findViewById<FrameLayout>(R.id.myProfileAvatar)
        val tvMyUsername = findViewById<TextView>(R.id.tvMyUsername)
        val btnEditProfile = findViewById<TextView>(R.id.btnEditProfile)
        val btnCreateProfile = findViewById<TextView>(R.id.btnCreateProfileFromFriends)

        layoutNotRegistered.visibility   = if (!isRegistered) View.VISIBLE else View.GONE
        layoutFriendsContent.visibility  = if (isRegistered) View.VISIBLE else View.GONE

        myProfileAvatar.removeAllViews()
        myProfileAvatar.addView(
            AvatarUtils.buildView(this, PrefsManager.getAvatarIndex(this), PrefsManager.getAvatarColor(this), 44)
        )

        tvMyUsername.text = if (isRegistered) "@${PrefsManager.getGlobalUsername(this)}" else "Not registered"

        btnEditProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.rowMyProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        btnCreateProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private fun loadData() {
        GlobalLeaderboard.ensureSignedIn(
            onReady = { uid ->
                myUid = uid
                FriendsManager.getFollowing(uid) { list ->
                    following = list
                    myFollowingUids = list.map { it.uid }.toSet()
                    val allUids = (listOf(uid) + list.map { it.uid }).distinct()
                    var pending = 2
                    fun done() {
                        if (--pending == 0) {
                            ranks = computeRanks(groupScores)
                            runOnUiThread { buildFriendsList() }
                        }
                    }
                    FriendsManager.fetchAllGroupScores(allUids) { scores ->
                        groupScores = scores
                        done()
                    }
                    FriendsManager.checkMutuals(uid, list.map { it.uid }) { mut ->
                        mutuals = mut
                        done()
                    }
                }
            },
            onError = { msg ->
                runOnUiThread {
                    Toast.makeText(this, "Error loading friends: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // ── MY FRIENDS tab ────────────────────────────────────────────────────────

    private fun buildFriendsList() {
        val uid = myUid ?: return
        val container = findViewById<LinearLayout>(R.id.friendsListContainer) ?: return
        val tvLoading = findViewById<TextView>(R.id.tvFriendsLoading)
        val tvEmpty   = findViewById<TextView>(R.id.tvFriendsEmpty)

        tvLoading.visibility = View.GONE
        container.removeAllViews()

        updateOwnRankChips(uid)

        if (following.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
            following.forEach { entry ->
                container.addView(buildFriendRow(entry, entry.uid in mutuals))
            }
        }
    }

    private fun updateOwnRankChips(myUid: String) {
        val chipsContainer = findViewById<LinearLayout>(R.id.myRankChips) ?: return
        chipsContainer.removeAllViews()
        val myRanks = ranks[myUid] ?: return
        val dp = resources.displayMetrics.density
        games.forEach { game ->
            val rank = myRanks[game.key] ?: return@forEach
            chipsContainer.addView(makeRankChip(game.iconRes, game.key, rank, dp))
        }
    }

    private fun buildFriendRow(entry: FollowEntry, isMutual: Boolean): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = if (isMutual) {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * dp
                    setColor(0x1100CC66.toInt())
                    setStroke((1 * dp).toInt(), 0x8800CC66.toInt())
                }
            } else {
                getDrawable(R.drawable.bg_game_tile)!!
            }
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * dp).toInt() }
        }

        // Tap row → show profile dialog
        row.setOnClickListener {
            GlobalLeaderboard.fetchUserInfo(entry.uid) { country, state ->
                val globalEntry = GlobalEntry(
                    uid         = entry.uid,
                    username    = entry.username,
                    country     = country,
                    state       = state,
                    avatarIndex = entry.avatarIndex,
                    avatarColor = entry.avatarColor
                )
                runOnUiThread {
                    showPlayerProfileDialog(
                        activity    = this,
                        entry       = globalEntry,
                        myUid       = myUid,
                        isFollowing = following.any { it.uid == entry.uid },
                        onFollowChanged = { isNowFollowing ->
                            if (!isNowFollowing) {
                                following = following.filter { it.uid != entry.uid }
                                myFollowingUids = myFollowingUids - entry.uid
                                mutuals = mutuals - entry.uid
                                buildFriendsList()
                            }
                        }
                    )
                }
            }
        }

        row.addView(AvatarUtils.buildView(this, entry.avatarIndex, entry.avatarColor, 40))

        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * dp).toInt()
            }
        }
        val nameColor = if (isMutual) Color.parseColor("#00CC66") else getColor(R.color.text)
        nameCol.addView(TextView(this).apply {
            text = "@${entry.username}" + if (isMutual) "  ♻" else ""
            setTextColor(nameColor)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
        })

        val rankChips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * dp).toInt() }
        }
        val entryRanks = ranks[entry.uid] ?: emptyMap()
        games.forEach { game ->
            val rank = entryRanks[game.key] ?: return@forEach
            rankChips.addView(makeRankChip(game.iconRes, game.key, rank, dp))
        }
        nameCol.addView(rankChips)
        row.addView(nameCol)

        row.addView(TextView(this).apply {
            text = "✕"
            setTextColor(getColor(R.color.muted))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), 0, (4 * dp).toInt())
            setOnClickListener { confirmUnfollow(entry) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        return row
    }

    /** Rank chip: small game icon + "#N" text, colored by game accent */
    private fun makeRankChip(iconRes: Int, gameKey: String, rank: Int, dp: Float): View {
        val color = when (gameKey) {
            PrefsManager.GAME_SNAKE        -> Color.parseColor("#4f8ef7")
            PrefsManager.GAME_PONG         -> Color.parseColor("#e74c3c")
            PrefsManager.GAME_ASTEROIDS    -> Color.parseColor("#00d4ff")
            PrefsManager.GAME_BRICKBREAKER -> Color.parseColor("#f1c40f")
            PrefsManager.GAME_CAVEDRIVER   -> Color.parseColor("#00FFCC")
            "blockdrop"                    -> Color.parseColor("#FF6B35")
            "memorymatch"                  -> Color.parseColor("#00FF96")
            else -> getColor(R.color.muted)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (6 * dp).toInt() }
        }
        val iconSize = (13 * dp).toInt()
        // Cave Diver uses a dedicated ship-silhouette icon for rank chips
        val resolvedIconRes = if (gameKey == PrefsManager.GAME_CAVEDRIVER) R.drawable.ic_cavedriver_ship else iconRes
        container.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize * 2 / 3)
            setImageResource(resolvedIconRes)
            setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
        })
        container.addView(TextView(this).apply {
            text = "#$rank"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(color)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (2 * dp).toInt() }
        })
        return container
    }

    private fun confirmUnfollow(entry: FollowEntry) {
        AlertDialog.Builder(this)
            .setTitle("Unfollow @${entry.username}?")
            .setPositiveButton("REMOVE") { _, _ ->
                val uid = myUid ?: return@setPositiveButton
                FriendsManager.unfollow(uid, entry.uid,
                    onSuccess = {
                        following = following.filter { it.uid != entry.uid }
                        runOnUiThread {
                            ranks = computeRanks(groupScores)
                            buildFriendsList()
                        }
                    },
                    onError = {
                        runOnUiThread {
                            Toast.makeText(this, "Error removing friend", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // ── Add friend ────────────────────────────────────────────────────────────

    private fun setupAddFriendButton() {
        val etAddFriend  = findViewById<EditText>(R.id.etAddFriend)  ?: return
        val btnAddFriend = findViewById<TextView>(R.id.btnAddFriend) ?: return
        val tvStatus     = findViewById<TextView>(R.id.tvAddFriendStatus) ?: return

        btnAddFriend.setOnClickListener {
            val uid = myUid ?: run {
                tvStatus.text = "Still loading — please wait"
                tvStatus.setTextColor(getColor(R.color.muted))
                tvStatus.visibility = View.VISIBLE
                return@setOnClickListener
            }

            val username = etAddFriend.text.toString().lowercase().trim()
            val myUsername = PrefsManager.getGlobalUsername(this) ?: ""

            when {
                username.length < 3 -> {
                    tvStatus.text = "Enter a valid username (3+ chars)"
                    tvStatus.setTextColor(getColor(R.color.accent_red))
                    tvStatus.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                username == myUsername -> {
                    tvStatus.text = "You can't follow yourself!"
                    tvStatus.setTextColor(getColor(R.color.accent_red))
                    tvStatus.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                following.any { it.username == username } -> {
                    tvStatus.text = "Already following @$username"
                    tvStatus.setTextColor(getColor(R.color.muted))
                    tvStatus.visibility = View.VISIBLE
                    return@setOnClickListener
                }
            }

            tvStatus.text = "Searching..."
            tvStatus.setTextColor(getColor(R.color.muted))
            tvStatus.visibility = View.VISIBLE
            btnAddFriend.isEnabled = false
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(etAddFriend.windowToken, 0)

            FriendsManager.searchUser(username,
                onFound = { theirUid, avatarIndex, avatarColor ->
                    FriendsManager.follow(uid, theirUid, username, avatarIndex, avatarColor,
                        onSuccess = {
                            runOnUiThread {
                                etAddFriend.setText("")
                                tvStatus.text = "Now following @$username!"
                                tvStatus.setTextColor(Color.parseColor("#2ecc71"))
                                tvStatus.visibility = View.VISIBLE
                                btnAddFriend.isEnabled = true
                            }
                            loadData()
                        },
                        onError = {
                            runOnUiThread {
                                tvStatus.text = "Error — try again"
                                tvStatus.setTextColor(getColor(R.color.accent_red))
                                btnAddFriend.isEnabled = true
                            }
                        }
                    )
                },
                onNotFound = {
                    runOnUiThread {
                        tvStatus.text = "User '@$username' not found"
                        tvStatus.setTextColor(getColor(R.color.accent_red))
                        btnAddFriend.isEnabled = true
                    }
                },
                onError = {
                    runOnUiThread {
                        tvStatus.text = "Error searching — check connection"
                        tvStatus.setTextColor(getColor(R.color.accent_red))
                        btnAddFriend.isEnabled = true
                    }
                }
            )
        }
    }

    // ── LEADERBOARD tab ───────────────────────────────────────────────────────

    private fun loadLeaderboard() {
        setupLeaderboardFilters()
        setupTimeRangeBar()
        fetchAndShowLeaderboard()
    }

    private fun setupTimeRangeBar() {
        val bar = findViewById<LinearLayout>(R.id.leaderboardTimeBar) ?: return
        val tvInfo = findViewById<TextView>(R.id.tvTimeRangeInfo) ?: return
        bar.removeAllViews()
        val dp = resources.displayMetrics.density

        data class Range(val label: String, val range: TimeRange)
        val ranges = listOf(Range("WEEK", TimeRange.WEEK), Range("MONTH", TimeRange.MONTH), Range("ALL TIME", TimeRange.ALL_TIME))

        fun refreshStyles() {
            ranges.forEachIndexed { i, r ->
                val chip = bar.getChildAt(i) as? TextView ?: return@forEachIndexed
                val active = r.range == selectedTimeRange
                chip.setTextColor(if (active) Color.WHITE else getColor(R.color.muted))
                chip.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16 * dp
                    if (active) setColor(getColor(R.color.accent_blue))
                    else { setColor(Color.TRANSPARENT); setStroke((1 * dp).toInt(), getColor(R.color.muted)) }
                }
            }
            // Days remaining subtitle
            when (selectedTimeRange) {
                TimeRange.WEEK -> {
                    val days = daysLeftInWeek()
                    tvInfo.text = "$days day${if (days == 1) "" else "s"} left in this week (Sun–Sat)"
                    tvInfo.visibility = View.VISIBLE
                }
                TimeRange.MONTH -> {
                    val days = daysLeftInMonth()
                    tvInfo.text = "$days day${if (days == 1) "" else "s"} left in this month"
                    tvInfo.visibility = View.VISIBLE
                }
                TimeRange.ALL_TIME -> tvInfo.visibility = View.GONE
            }
        }

        ranges.forEach { r ->
            val chip = TextView(this).apply {
                text = r.label
                textSize = 11f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (30 * dp).toInt()
                ).apply { marginStart = (8 * dp).toInt() }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedTimeRange = r.range
                    refreshStyles()
                    fetchAndShowLeaderboard()
                }
            }
            bar.addView(chip)
        }
        refreshStyles()
    }

    private fun setupLeaderboardFilters() {
        val filterRow = findViewById<LinearLayout>(R.id.leaderboardFilterRow) ?: return
        filterRow.removeAllViews()
        val dp = resources.displayMetrics.density

        games.forEach { game ->
            val chip = TextView(this).apply {
                text = " ${game.label}"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (34 * dp).toInt()
                ).apply { marginEnd = (8 * dp).toInt() }
                // Game icon as compound drawable
                val iconSize = (16 * dp).toInt()
                val d = ContextCompat.getDrawable(this@FriendsActivity, game.iconRes)
                d?.setBounds(0, 0, iconSize, iconSize)
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = (4 * dp).toInt()
            }
            filterRow.addView(chip)
        }

        fun refreshChipStyles() {
            for (i in games.indices) {
                val chip = filterRow.getChildAt(i) as? TextView ?: continue
                val isActive = games[i].key == selectedGame
                chip.setTextColor(if (isActive) Color.WHITE else getColor(R.color.muted))
                chip.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 17 * dp
                    if (isActive) {
                        setColor(getColor(R.color.accent_blue))
                    } else {
                        setColor(Color.TRANSPARENT)
                        setStroke((1 * dp).toInt(), getColor(R.color.muted))
                    }
                }
            }
        }

        for (i in games.indices) {
            (filterRow.getChildAt(i) as? TextView)?.setOnClickListener {
                selectedGame = games[i].key
                refreshChipStyles()
                fetchAndShowLeaderboard()
            }
        }
        refreshChipStyles()
    }

    private fun fetchAndShowLeaderboard() {
        val tvLoading = findViewById<TextView>(R.id.tvLeaderboardLoading) ?: return
        val tvEmpty   = findViewById<TextView>(R.id.tvLeaderboardEmpty) ?: return
        val container = findViewById<LinearLayout>(R.id.leaderboardContainer) ?: return

        tvLoading.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        container.removeAllViews()

        val uid = myUid ?: run { tvLoading.text = "Sign in required"; return }
        val allUids = (listOf(uid) + following.map { it.uid }).distinct()

        FriendsManager.fetchFriendsScores(allUids, selectedGame, selectedTimeRange) { entries ->
            runOnUiThread {
                tvLoading.visibility = View.GONE
                if (entries.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    entries.forEachIndexed { idx, entry ->
                        container.addView(
                            buildLeaderboardRow(entry, idx + 1, entry.uid == uid, entry.uid in mutuals)
                        )
                    }
                }
            }
        }
    }

    private fun buildLeaderboardRow(
        entry: GlobalEntry,
        rank: Int,
        isMe: Boolean,
        isMutual: Boolean
    ): View {
        val dp = resources.displayMetrics.density
        // Medals (gold/silver/bronze) only for timed periods, not ALL TIME
        val useMedals = selectedTimeRange != TimeRange.ALL_TIME
        val rankColor = when {
            useMedals && rank == 1 -> Color.parseColor("#FFD700")
            useMedals && rank == 2 -> Color.parseColor("#C0C0C0")
            useMedals && rank == 3 -> Color.parseColor("#CD7F32")
            isMe     -> getColor(R.color.accent_blue)
            isMutual -> Color.parseColor("#00CC66")
            else     -> getColor(R.color.muted)
        }
        val nameColor = when {
            isMe    -> getColor(R.color.accent_blue)
            isMutual -> Color.parseColor("#00CC66")
            else    -> getColor(R.color.text)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6 * dp
                setColor(when {
                    isMe    -> 0x220088FF.toInt()
                    isMutual -> 0x1100CC66.toInt()
                    else    -> Color.TRANSPARENT
                })
            }
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4 * dp).toInt() }
        }

        // Tap row → show player profile dialog
        row.setOnClickListener {
            showPlayerProfileDialog(
                activity    = this,
                entry       = entry,
                myUid       = myUid,
                isFollowing = myFollowingUids.contains(entry.uid),
                onFollowChanged = { isNowFollowing ->
                    myFollowingUids = if (isNowFollowing) myFollowingUids + entry.uid
                                     else myFollowingUids - entry.uid
                }
            )
        }

        row.addView(TextView(this).apply {
            text = "#$rank"
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(rankColor)
            layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        row.addView(AvatarUtils.buildView(this, entry.avatarIndex, entry.avatarColor, 32))

        row.addView(TextView(this).apply {
            text = "@${entry.username}" + if (isMe) " (me)" else if (isMutual) " ♻" else ""
            setTextColor(nameColor)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (10 * dp).toInt()
            }
        })

        // Score column with game icon
        val gameInfo = games.find { it.key == selectedGame }
        val scoreText = formatGlobalScore(selectedGame, entry.score, entry.mode)
        row.addView(TextView(this).apply {
            text = " $scoreText"
            setTextColor(nameColor)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            if (gameInfo != null) {
                val iconSize = (16 * dp).toInt()
                val d = ContextCompat.getDrawable(this@FriendsActivity, gameInfo.iconRes)
                d?.setBounds(0, 0, iconSize, iconSize)
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = (2 * dp).toInt()
            }
        })

        return row
    }

    // ── Rank computation ──────────────────────────────────────────────────────

    private fun computeRanks(scores: Map<String, Map<String, Int>>): Map<String, Map<String, Int>> {
        val result = mutableMapOf<String, MutableMap<String, Int>>()
        games.forEach { game ->
            scores.entries
                .mapNotNull { (uid, gameMap) -> gameMap[game.key]?.let { uid to it } }
                .sortedByDescending { it.second }
                .forEachIndexed { idx, (uid, _) ->
                    result.getOrPut(uid) { mutableMapOf() }[game.key] = idx + 1
                }
        }
        return result
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyWindowBackground(this)
        val bg = ThemeManager.currentBgColor(this)
        findViewById<LinearLayout>(R.id.friendsRootLayout)?.setBackgroundColor(bg)
        setupMyProfileSection()
    }
}
