package com.pocketarcade

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.MobileAds
import com.pocketarcade.ads.AdManager
import com.pocketarcade.billing.BillingManager
import com.pocketarcade.games.asteroids.AsteroidsActivity
import com.pocketarcade.games.brickbreaker.BrickBreakerActivity
import com.pocketarcade.games.pong.PongActivity
import com.pocketarcade.games.snake.SnakeActivity
import com.pocketarcade.storage.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var billing: BillingManager
    private var upsellDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this) {}

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        billing = BillingManager(
            activity = this,
            onPurchased = {
                AdManager.populateBannerContainer(findViewById(R.id.adContainer))
                upsellDialog?.dismiss()
            },
            onError = {}
        )
        billing.connect()

        bindTile(R.id.tileSnake, SnakeActivity::class.java)
        bindTile(R.id.tilePong, PongActivity::class.java)
        bindTile(R.id.tileAsteroids, AsteroidsActivity::class.java)
        bindTile(R.id.tileBrickBreaker, BrickBreakerActivity::class.java)

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateScores()
    }

    private fun bindTile(tileId: Int, activityClass: Class<*>) {
        findViewById<View>(tileId).setOnClickListener {
            startActivity(Intent(this, activityClass))
        }
    }

    override fun onResume() {
        super.onResume()
        updateScores()
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        if (PrefsManager.onSessionStart(this)) {
            showUpsellDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billing.disconnect()
    }

    private fun updateScores() {
        findViewById<TextView>(R.id.tvScoreSnake).text =
            "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_SNAKE)}"
        findViewById<TextView>(R.id.tvScorePong).text =
            if (PrefsManager.getHighScore(this, PrefsManager.GAME_PONG) > 0) "WON!" else "High Score: 0"
        findViewById<TextView>(R.id.tvScoreAsteroids).text =
            "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_ASTEROIDS)}"
        findViewById<TextView>(R.id.tvScoreBrickBreaker).text =
            "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_BRICKBREAKER)}"
    }

    // ── Upsell dialog ─────────────────────────────────────────────────────────

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
