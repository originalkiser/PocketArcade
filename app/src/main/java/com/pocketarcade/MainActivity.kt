package com.pocketarcade

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.MobileAds
import com.pocketarcade.ads.AdManager
import com.pocketarcade.billing.BillingManager
import com.pocketarcade.games.asteroids.AsteroidsActivity
import com.pocketarcade.games.brickbreaker.BrickBreakerActivity
import com.pocketarcade.games.cavedriver2.CaveDiverActivity
import com.pocketarcade.games.blockpop.BlockDropActivity
import com.pocketarcade.games.blockpop.BlockDropDifficulty
import com.pocketarcade.games.pong.PongActivity
import com.pocketarcade.games.memorymatch.MemoryMatchActivity
import com.pocketarcade.games.snake.SnakeActivity
import com.pocketarcade.leaderboard.FriendsManager
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.leaderboard.LeaderboardManager
import com.pocketarcade.leaderboard.TimeRange
import com.pocketarcade.leaderboard.formatGlobalScore
import com.pocketarcade.leaderboard.showGlobalLeaderboardPicker
import com.pocketarcade.DisplacementManager
import com.pocketarcade.ScoreSyncManager
import com.pocketarcade.leaderboard.showRegistrationPromptIfNeeded
import com.pocketarcade.leaderboard.showUsernameSetupDialog
import com.pocketarcade.storage.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var billing: BillingManager
    private var upsellDialog: AlertDialog? = null
    private val blinkHandler = Handler(Looper.getMainLooper())
    private var blinkVisible = true
    /** Snapshot of games-played count at the START of the last onResume. */
    private var gamesPlayedAtLastResume = -1

    // ── Ticker external data cache ─────────────────────────────────────────────
    /** game key → (display name, raw score). Populated by [loadGlobalTickerData] /
     *  [loadFriendsTickerData] and consumed by [buildMarqueeTicker]. */
    private val tickerCache = mutableMapOf<String, Pair<String, Int>>()

    private val TICKER_GAME_KEYS = listOf(
        PrefsManager.GAME_SNAKE, PrefsManager.GAME_PONG,
        PrefsManager.GAME_ASTEROIDS, CaveDiverActivity.GAME_KEY,
        PrefsManager.GAME_BRICKBREAKER
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PrefsManager.migrateInflatedTime(this)

        MobileAds.initialize(this) {
            runOnUiThread { AdManager.populateBannerContainer(findViewById(R.id.adContainer)) }
        }

        billing = BillingManager(
            activity = this,
            onPurchased = {
                AdManager.populateBannerContainer(findViewById(R.id.adContainer))
                upsellDialog?.dismiss()
            },
            onError = {}
        )
        billing.connect()

        bindTile(R.id.tileSnake,        SnakeActivity::class.java)
        bindTile(R.id.tilePong,         PongActivity::class.java)
        bindTile(R.id.tileAsteroids,    AsteroidsActivity::class.java)
        bindTile(R.id.tileBrickBreaker, BrickBreakerActivity::class.java)
        bindTile(R.id.tileCaveDiver,    CaveDiverActivity::class.java)
        bindTile(R.id.tileBlockPop,     BlockDropActivity::class.java)
        // Memory Match: difficulty picker shows inside the activity (in front of the board)
        bindTile(R.id.tileMemoryMatch, MemoryMatchActivity::class.java)

        applyTileBorders()

        findViewById<LinearLayout>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnFriends).setOnClickListener {
            if (PrefsManager.getGlobalUsername(this) == null) {
                GlobalLeaderboard.ensureSignedIn(
                    onReady = { uid ->
                        runOnUiThread {
                            showUsernameSetupDialog(this, uid, pendingScore = null, onSuccess = {
                                startActivity(Intent(this, FriendsActivity::class.java))
                            })
                        }
                    },
                    onError = { msg ->
                        runOnUiThread {
                            showRegistrationPromptIfNeeded(this)
                        }
                    }
                )
            } else {
                startActivity(Intent(this, FriendsActivity::class.java))
            }
        }
        // Marquee requires isSelected = true to scroll
        findViewById<TextView>(R.id.tvMarquee).isSelected = true

        // ── Ticker source buttons ──────────────────────────────────────────────
        findViewById<TextView>(R.id.tickerBtnLocal).setOnClickListener {
            selectTickerSource(PrefsManager.TickerSource.LOCAL)
        }
        findViewById<TextView>(R.id.tickerBtnGlobal).setOnClickListener {
            selectTickerSource(PrefsManager.TickerSource.GLOBAL)
        }
        findViewById<TextView>(R.id.tickerBtnFriends).setOnClickListener {
            selectTickerSource(PrefsManager.TickerSource.FRIENDS)
        }
        refreshTickerButtons()

        updateScores()
        IconRotationWorker.schedule(this)
        UpdateChecker.check(this)

        // Show splash curtain — changelog then registration prompt after curtain lifts
        if (savedInstanceState == null) {
            val root = findViewById<FrameLayout>(R.id.rootFrame)
            val splash = SplashView(this)
            splash.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            splash.onDone = {
                root.removeView(splash)
                // Update dialog takes precedence — skip changelog/onboarding if it's already visible.
                if (!UpdateChecker.updateDialogShown) {
                    showChangelogIfNeeded(this) {
                        showRegistrationPromptIfNeeded(this)
                        // Displacement popup appears after What's New and update dialogs.
                        DisplacementManager.checkAndShowIfNeeded(this)
                    }
                }
            }
            root.addView(splash)
            splash.post { splash.start() }
        }

        findViewById<LinearLayout>(R.id.btnLeaderboards).setOnClickListener {
            showGlobalLeaderboardPicker(this)
        }
    }

    private fun bindTile(tileId: Int, activityClass: Class<*>) {
        findViewById<View>(tileId).setOnClickListener {
            startActivity(Intent(this, activityClass))
        }
    }

    private fun applyTileBorders() {
        val d = resources.displayMetrics.density
        fun border(view: View, color: Int) {
            val gd = GradientDrawable()
            gd.setColor(Color.parseColor("#060614"))
            gd.cornerRadius = 8f * d
            gd.setStroke((1.5f * d).toInt(), color)
            view.background = gd
        }
        border(findViewById(R.id.tileSnake),        getColor(R.color.accent_blue))
        border(findViewById(R.id.tilePong),         getColor(R.color.accent_red))
        border(findViewById(R.id.tileAsteroids),    getColor(R.color.accent_cyan))
        border(findViewById(R.id.tileCaveDiver),    Color.parseColor("#00FFCC"))
        border(findViewById(R.id.tileBlockPop),     Color.parseColor("#FF6B35"))
        border(findViewById(R.id.tileBrickBreaker), getColor(R.color.accent_yellow))
        border(findViewById(R.id.tileMemoryMatch),  Color.parseColor("#00FF96"))
    }

    private fun startBlinkPrompt() {
        val tv = findViewById<TextView>(R.id.tvSelectGame)
        val blink = object : Runnable {
            override fun run() {
                tv.alpha = if (blinkVisible) 0.9f else 0.15f
                blinkVisible = !blinkVisible
                blinkHandler.postDelayed(this, 620)
            }
        }
        blinkHandler.post(blink)
    }

    override fun onResume() {
        super.onResume()
        UpdateChecker.checkResumeDownload(this)
        FriendNudgeManager.checkOnOpen(this)
        ScoreSyncManager.syncOnLaunch(this)
        refreshTickerButtons()
        when (PrefsManager.getTickerSource(this)) {
            PrefsManager.TickerSource.GLOBAL  -> loadGlobalTickerData()
            PrefsManager.TickerSource.FRIENDS -> loadFriendsTickerData()
            PrefsManager.TickerSource.LOCAL   -> Unit
        }
        updateScores()
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))
        ThemeManager.applyWindowBackground(this)
        findViewById<FrameLayout>(R.id.rootFrame).setBackgroundColor(ThemeManager.currentBgColor(this))

        refreshProfileCircle()

        // Only show upsell when the player actually finished a game since the last resume
        // (not on cold-start or when returning from Settings/Profile/etc.).
        val gamesNow = PrefsManager.getGamesPlayedTotal(this)
        if (gamesPlayedAtLastResume >= 0 && gamesNow > gamesPlayedAtLastResume) {
            if (PrefsManager.checkAndConsumeUpsellTrigger(this)) {
                showUpsellDialog()
            }
        }
        gamesPlayedAtLastResume = gamesNow

        startBlinkPrompt()

        val rootFrame = findViewById<FrameLayout>(R.id.rootFrame)
        val friendsBtn = findViewById<LinearLayout>(R.id.btnFriends)
        if (rootFrame != null && friendsBtn != null) {
            HintManager.showIfNeeded(this, rootFrame, friendsBtn,
                "Add friends to compare scores!", PrefsManager.HINT_FRIENDS)
        }
    }

    private fun refreshProfileCircle() {
        val circle = findViewById<FrameLayout>(R.id.profileCircle) ?: return
        circle.removeAllViews()
        circle.addView(
            AvatarUtils.buildView(this, PrefsManager.getAvatarIndex(this), PrefsManager.getAvatarColor(this), 56)
        )
    }

    override fun onPause() {
        super.onPause()
        blinkHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        billing.disconnect()
    }

    private fun updateScores() {
        fun topDetail(game: String): String {
            val top = LeaderboardManager.getEntries(this, game).firstOrNull() ?: return ""
            val initials = top.initials.trim()
            return if (initials.isNotEmpty()) "$initials · ${top.formattedDate}" else top.formattedDate
        }

        val pongWins = PrefsManager.getPongWins(this)
        val pongPlays = PrefsManager.getStatPlays(this, PrefsManager.GAME_PONG)
        val pongLosses = (pongPlays - pongWins).coerceAtLeast(0)
        val wlRatio = if (pongLosses > 0) "%.1f".format(pongWins.toFloat() / pongLosses) else if (pongWins > 0) "INF" else "-"
        findViewById<TextView>(R.id.tvScoreSnake).text =
            "High Score: %,d".format(PrefsManager.getHighScore(this, PrefsManager.GAME_SNAKE))
        findViewById<TextView>(R.id.tvScorePong).text = "Wins: $pongWins  W/L: $wlRatio"
        findViewById<TextView>(R.id.tvScoreAsteroids).text =
            "High Score: %,d".format(PrefsManager.getHighScore(this, PrefsManager.GAME_ASTEROIDS))
        findViewById<TextView>(R.id.tvScoreCaveDiver).text =
            "High Score: %,d".format(PrefsManager.getHighScore(this, CaveDiverActivity.GAME_KEY))
        fun bdStat(diff: BlockDropDifficulty): String {
            val score = PrefsManager.getHighScore(this, BlockDropActivity.scoreKey(diff))
            return if (score == 0) "--" else "%,d".format(score)
        }
        val bdE = bdStat(BlockDropDifficulty.EASY)
        val bdM = bdStat(BlockDropDifficulty.MEDIUM)
        val bdH = bdStat(BlockDropDifficulty.HARD)
        val bdBestAny = listOf(
            BlockDropDifficulty.EASY, BlockDropDifficulty.MEDIUM, BlockDropDifficulty.HARD
        ).mapNotNull { d ->
            val s = PrefsManager.getHighScore(this, BlockDropActivity.scoreKey(d))
            if (s > 0) s else null
        }.maxOrNull()
        findViewById<TextView>(R.id.tvScoreBlockPop).text =
            if (bdBestAny != null) "Best: %,d".format(bdBestAny) else "Best: --"
        findViewById<TextView>(R.id.tvScoreBlockPopDetail).text =
            "E: $bdE  M: $bdM  H: $bdH"
        findViewById<TextView>(R.id.tvScoreBrickBreaker).text =
            "High Score: %,d".format(PrefsManager.getHighScore(this, PrefsManager.GAME_BRICKBREAKER))

        fun mmStat(diff: String): String {
            val moves = PrefsManager.getMmBestMoves(this, diff)
            return if (moves == 0) "--" else "$moves moves"
        }
        findViewById<TextView>(R.id.tvScoreMemoryMatch).text =
            "E: ${mmStat("easy")}   M: ${mmStat("medium")}"
        findViewById<TextView>(R.id.tvScoreMemoryMatchDetail).text =
            "H: ${mmStat("hard")}"

        findViewById<TextView>(R.id.tvScoreSnakeDetail).text     = topDetail(PrefsManager.GAME_SNAKE)
        findViewById<TextView>(R.id.tvScorePongDetail).text      = ""
        findViewById<TextView>(R.id.tvScoreAsteroidsDetail).text = topDetail(PrefsManager.GAME_ASTEROIDS)
        findViewById<TextView>(R.id.tvScoreCaveDiverDetail).text = topDetail(CaveDiverActivity.GAME_KEY)
        findViewById<TextView>(R.id.tvScoreBBDetail).text        = topDetail(PrefsManager.GAME_BRICKBREAKER)
        // Note: tvScoreMemoryMatchDetail is already set above (H score) — do not overwrite it

        // Update score ticker
        val tvMarquee = findViewById<TextView>(R.id.tvMarquee)
        tvMarquee.text = buildMarqueeTicker()
        tvMarquee.isSelected = true
    }

    private fun buildMarqueeTicker(): String {
        data class G(val key: String, val label: String)
        val games = listOf(
            G(PrefsManager.GAME_SNAKE,        "SNAKE"),
            G(PrefsManager.GAME_PONG,         "PONG"),
            G(PrefsManager.GAME_ASTEROIDS,    "ASTEROIDS"),
            G(CaveDiverActivity.GAME_KEY,     "CAVE"),
            G(PrefsManager.GAME_BRICKBREAKER, "BRKR")
        )

        return when (PrefsManager.getTickerSource(this)) {
            PrefsManager.TickerSource.LOCAL -> {
                val parts = games.mapNotNull { (key, label) ->
                    val top = LeaderboardManager.getEntries(this, key).firstOrNull()
                        ?: return@mapNotNull null
                    val initials = top.initials.trim()
                    val score    = if (key == PrefsManager.GAME_PONG) "WON!" else "${top.score} PTS"
                    val inits    = if (initials.isNotEmpty()) "$initials · " else ""
                    "$label · $inits$score · ${top.formattedDate}"
                }
                if (parts.isEmpty()) "LOCAL BEST  ✦  PLAY TO SET RECORDS  ✦  "
                else "LOCAL BEST  ✦  ${parts.joinToString("  ✦  ")}  ✦  "
            }
            PrefsManager.TickerSource.GLOBAL, PrefsManager.TickerSource.FRIENDS -> {
                val parts = games.mapNotNull { (key, label) ->
                    val (username, score) = tickerCache[key] ?: return@mapNotNull null
                    "$label · @$username · ${formatGlobalScore(key, score)}"
                }
                val header = if (PrefsManager.getTickerSource(this) == PrefsManager.TickerSource.GLOBAL)
                    "WORLD BEST" else "FRIEND BEST"
                if (parts.isEmpty()) "$header  ✦  FETCHING SCORES…  ✦  "
                else "$header  ✦  ${parts.joinToString("  ✦  ")}  ✦  "
            }
        }
    }

    // ── Ticker source helpers ──────────────────────────────────────────────────

    private fun selectTickerSource(source: PrefsManager.TickerSource) {
        PrefsManager.setTickerSource(this, source)
        refreshTickerButtons()
        tickerCache.clear()
        when (source) {
            PrefsManager.TickerSource.GLOBAL  -> loadGlobalTickerData()
            PrefsManager.TickerSource.FRIENDS -> loadFriendsTickerData()
            PrefsManager.TickerSource.LOCAL   -> updateScores()
        }
    }

    private fun refreshTickerButtons() {
        val source  = PrefsManager.getTickerSource(this)
        val active  = getColor(R.color.accent_yellow)
        val inactive = getColor(R.color.muted)
        findViewById<TextView>(R.id.tickerBtnLocal)
            .setTextColor(if (source == PrefsManager.TickerSource.LOCAL)   active else inactive)
        findViewById<TextView>(R.id.tickerBtnGlobal)
            .setTextColor(if (source == PrefsManager.TickerSource.GLOBAL)  active else inactive)
        findViewById<TextView>(R.id.tickerBtnFriends)
            .setTextColor(if (source == PrefsManager.TickerSource.FRIENDS) active else inactive)
    }

    private fun loadGlobalTickerData() {
        val pending = java.util.concurrent.atomic.AtomicInteger(TICKER_GAME_KEYS.size)
        TICKER_GAME_KEYS.forEach { game ->
            GlobalLeaderboard.fetchGlobal(game, mode = null, limit = 1L) { entries ->
                entries.firstOrNull()?.let { e -> tickerCache[game] = e.username to e.score }
                if (pending.decrementAndGet() == 0) runOnUiThread { updateScores() }
            }
        }
    }

    private fun loadFriendsTickerData() {
        val uid = GlobalLeaderboard.currentUid ?: return
        FriendsManager.getFollowing(uid) { friends ->
            val uids = friends.map { it.uid }
            if (uids.isEmpty()) { runOnUiThread { updateScores() }; return@getFollowing }
            val pending = java.util.concurrent.atomic.AtomicInteger(TICKER_GAME_KEYS.size)
            TICKER_GAME_KEYS.forEach { game ->
                FriendsManager.fetchFriendsScores(uids, game, TimeRange.ALL_TIME) { entries ->
                    entries.maxByOrNull { it.score }
                        ?.let { e -> tickerCache[game] = e.username to e.score }
                    if (pending.decrementAndGet() == 0) runOnUiThread { updateScores() }
                }
            }
        }
    }

    // ── Upsell dialog ──────────────────────────────────────────────────────────

    private fun showUpsellDialog() {
        if (PrefsManager.isAdFree(this)) return
        if (upsellDialog?.isShowing == true) return

        val contentView = layoutInflater.inflate(R.layout.dialog_upsell, null)
        val tvDismiss = contentView.findViewById<TextView>(R.id.tvDismiss)
        val btnBuy = contentView.findViewById<TextView>(R.id.btnBuy)

        val dialog = AlertDialog.Builder(this)
            .setView(contentView)
            .setCancelable(false)
            .create()
        upsellDialog = dialog

        tvDismiss.isEnabled = false
        object : CountDownTimer(3_000, 1_000) {
            override fun onTick(ms: Long) {
                tvDismiss.text = getString(R.string.upsell_dismiss, (ms / 1000 + 1).toInt())
            }
            override fun onFinish() {
                tvDismiss.isEnabled = true
                tvDismiss.text = "No thanks"
            }
        }.start()

        tvDismiss.setOnClickListener { dialog.dismiss() }
        btnBuy.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AdFreeActivity::class.java))
        }

        dialog.show()
    }
}
