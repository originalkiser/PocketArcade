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
import com.pocketarcade.games.pong.PongActivity
import com.pocketarcade.games.snake.SnakeActivity
import com.pocketarcade.leaderboard.LeaderboardManager
import com.pocketarcade.leaderboard.showGlobalLeaderboardPicker
import com.pocketarcade.leaderboard.showRegistrationPromptIfNeeded
import com.pocketarcade.leaderboard.showUsernameSetupDialog
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.storage.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var billing: BillingManager
    private var upsellDialog: AlertDialog? = null
    private val blinkHandler = Handler(Looper.getMainLooper())
    private var blinkVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        bindTile(R.id.tileSnake,       SnakeActivity::class.java)
        bindTile(R.id.tilePong,        PongActivity::class.java)
        bindTile(R.id.tileAsteroids,   AsteroidsActivity::class.java)
        bindTile(R.id.tileBrickBreaker, BrickBreakerActivity::class.java)

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
        findViewById<LinearLayout>(R.id.btnRecordBook).setOnClickListener {
            startActivity(Intent(this, RecordBookActivity::class.java))
        }

        // Marquee requires isSelected = true to scroll
        findViewById<TextView>(R.id.tvMarquee).isSelected = true

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
                showChangelogIfNeeded(this) {
                    showRegistrationPromptIfNeeded(this)
                }
            }
            root.addView(splash)
            splash.post { splash.start() }
        }

        findViewById<TextView>(R.id.btnGlobalScores).setOnClickListener {
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
        border(findViewById(R.id.tileBrickBreaker), getColor(R.color.accent_yellow))
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
        updateScores()
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))
        ThemeManager.applyWindowBackground(this)
        findViewById<FrameLayout>(R.id.rootFrame).setBackgroundColor(ThemeManager.currentBgColor(this))

        refreshProfileCircle()

        if (PrefsManager.checkAndConsumeUpsellTrigger(this)) {
            showUpsellDialog()
        }
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
            "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_SNAKE)}"
        findViewById<TextView>(R.id.tvScorePong).text = "Wins: $pongWins  W/L: $wlRatio"
        findViewById<TextView>(R.id.tvScoreAsteroids).text =
            "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_ASTEROIDS)}"
        findViewById<TextView>(R.id.tvScoreBrickBreaker).text =
            "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_BRICKBREAKER)}"

        findViewById<TextView>(R.id.tvScoreSnakeDetail).text    = topDetail(PrefsManager.GAME_SNAKE)
        findViewById<TextView>(R.id.tvScorePongDetail).text     = ""
        findViewById<TextView>(R.id.tvScoreAsteroidsDetail).text = topDetail(PrefsManager.GAME_ASTEROIDS)
        findViewById<TextView>(R.id.tvScoreBBDetail).text       = topDetail(PrefsManager.GAME_BRICKBREAKER)

        // Record Book summary card
        updateRecordBookSummary()

        // Update score ticker
        val tvMarquee = findViewById<TextView>(R.id.tvMarquee)
        tvMarquee.text = buildMarqueeTicker()
        tvMarquee.isSelected = true
    }

    private fun updateRecordBookSummary() {
        val games = listOf(PrefsManager.GAME_SNAKE, PrefsManager.GAME_PONG,
            PrefsManager.GAME_ASTEROIDS, PrefsManager.GAME_BRICKBREAKER)
        val total = games.sumOf { PrefsManager.getStatPlays(this, it) }
        val tv = findViewById<TextView>(R.id.tvRecordBookSummary)
        if (total == 0) {
            tv.text = "NO GAMES LOGGED YET"
            return
        }
        val labels = mapOf(
            PrefsManager.GAME_SNAKE        to "SNAKE",
            PrefsManager.GAME_PONG         to "PONG",
            PrefsManager.GAME_ASTEROIDS    to "ASTEROIDS",
            PrefsManager.GAME_BRICKBREAKER to "BRICK BREAKER"
        )
        val topGame = games.maxByOrNull { PrefsManager.getStatPlays(this, it) }!!
        val topPlays = PrefsManager.getStatPlays(this, topGame)
        tv.text = "$total GAMES\nMost Played: ${labels[topGame]} - $topPlays games"
    }

    private fun buildMarqueeTicker(): String {
        data class G(val key: String, val label: String, val isPong: Boolean)
        val games = listOf(
            G(PrefsManager.GAME_SNAKE,        "SNAKE",     false),
            G(PrefsManager.GAME_PONG,         "PONG",      true),
            G(PrefsManager.GAME_ASTEROIDS,    "ASTEROIDS", false),
            G(PrefsManager.GAME_BRICKBREAKER, "BRKR",      false)
        )
        val parts = games.map { (key, label, isPong) ->
            val top = LeaderboardManager.getEntries(this, key).firstOrNull()
            if (top == null) {
                "$label · NO SCORES"
            } else {
                val initials = top.initials.trim()
                val score    = if (isPong) "WON!" else "${top.score} PTS"
                val inits    = if (initials.isNotEmpty()) "$initials · " else ""
                "$label · $inits$score · ${top.formattedDate}"
            }
        }
        return "TOP SCORES  ✦  ${parts.joinToString("  ✦  ")}  ✦  "
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
        btnBuy.setOnClickListener { billing.launchPurchaseFlow() }

        dialog.show()
    }
}
