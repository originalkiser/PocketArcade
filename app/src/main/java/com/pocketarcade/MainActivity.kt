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

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.btnCredits).setOnClickListener {
            showCreditsDialog(this)
        }

        // Marquee requires isSelected = true to scroll
        findViewById<TextView>(R.id.tvMarquee).isSelected = true

        updateScores()
        startBlinkPrompt()
        IconRotationWorker.schedule(this)
        UpdateChecker.check(this)

        // Show splash curtain — changelog appears only after curtain lifts
        if (savedInstanceState == null) {
            val root = findViewById<FrameLayout>(R.id.rootFrame)
            val splash = SplashView(this)
            splash.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            splash.onDone = {
                root.removeView(splash)
                showChangelogIfNeeded(this)
            }
            root.addView(splash)
            splash.post { splash.start() }
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
        findViewById<FrameLayout>(R.id.rootFrame).setBackgroundColor(ThemeManager.currentTheme(this).bg)

        if (PrefsManager.checkAndConsumeUpsellTrigger(this)) {
            showUpsellDialog()
        }
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

        val pongScore = PrefsManager.getHighScore(this, PrefsManager.GAME_PONG)
        findViewById<TextView>(R.id.tvScoreSnake).text =
            "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_SNAKE)}"
        findViewById<TextView>(R.id.tvScorePong).text =
            if (pongScore > 0) "WON!" else "High Score: 0"
        findViewById<TextView>(R.id.tvScoreAsteroids).text =
            "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_ASTEROIDS)}"
        findViewById<TextView>(R.id.tvScoreBrickBreaker).text =
            "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_BRICKBREAKER)}"

        findViewById<TextView>(R.id.tvScoreSnakeDetail).text    = topDetail(PrefsManager.GAME_SNAKE)
        findViewById<TextView>(R.id.tvScorePongDetail).text     = topDetail(PrefsManager.GAME_PONG)
        findViewById<TextView>(R.id.tvScoreAsteroidsDetail).text = topDetail(PrefsManager.GAME_ASTEROIDS)
        findViewById<TextView>(R.id.tvScoreBBDetail).text       = topDetail(PrefsManager.GAME_BRICKBREAKER)

        // Update score ticker
        val tvMarquee = findViewById<TextView>(R.id.tvMarquee)
        tvMarquee.text = buildMarqueeTicker()
        tvMarquee.isSelected = true
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
