package com.pocketarcade.games.cavedriver2

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.R
import com.pocketarcade.ScoreSyncManager
import com.pocketarcade.ThemeManager
import com.pocketarcade.Themes
import com.pocketarcade.ads.AdManager
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.leaderboard.checkAndShowLeaderboard
import com.pocketarcade.leaderboard.handlePostGameLeaderboards
import com.pocketarcade.showThemePickerDialog
import com.pocketarcade.storage.PrefsManager

class CaveDiverActivity : AppCompatActivity() {

    private lateinit var gameView:   CaveDiverView
    private lateinit var thrustZone: FrameLayout
    private var gameStartTime = 0L

    companion object {
        const val GAME_KEY = "cavedriver"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.pocketarcade.SystemColorTheme.applyIfActive(this, this)
        setContentView(R.layout.activity_cavedriver2)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        gameView   = findViewById(R.id.caveDiver2View)
        thrustZone = findViewById(R.id.thrustZone2)

        gameView.loadBestScore(PrefsManager.getHighScore(this, GAME_KEY))

        applyTheme()

        findViewById<TextView>(R.id.btnBack2).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnSettings2).setOnClickListener { startActivity(android.content.Intent(this, com.pocketarcade.SettingsActivity::class.java)) }

        gameView.onGameStarted = {
            gameStartTime = System.currentTimeMillis()
        }

        gameView.onGameOver = { score ->
            val duration = System.currentTimeMillis() - gameStartTime
            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, GAME_KEY, score, duration)
            PrefsManager.setHighScore(this, GAME_KEY, score)

            ScoreSyncManager.recordGameScore(this, GAME_KEY, "_", score)

            val username = PrefsManager.getGlobalUsername(this)
            if (username != null) {
                GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
                    GlobalLeaderboard.submitScore(
                        uid, username, GAME_KEY, score,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        null,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                    GlobalLeaderboard.submitPeriodScore(
                        uid, username, GAME_KEY, score,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        null,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                })
            }

            runOnUiThread {
                checkAndShowLeaderboard(this, GAME_KEY, score) {
                    handlePostGameLeaderboards(this, GAME_KEY, null, score)
                }
            }
        }

        // Thrust zone below game canvas mirrors in-canvas touch.
        // Colors are theme-aware: dark mode keeps the original near-black feedback;
        // light mode uses card-grey (pressed) / bg-white (released) instead.
        wireThrustZone()
    }

    override fun onResume() {
        super.onResume()
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))
        applyTheme()
        wireThrustZone()
    }

    override fun onPause() {
        super.onPause()
        gameView.setThrusting(false)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun wireThrustZone() {
        val isLight = ThemeManager.isLightMode(this)
        val bgNormal  = if (isLight) getColor(R.color.bg)   else Color.parseColor("#050510")
        val bgPressed = if (isLight) getColor(R.color.card) else Color.parseColor("#0a0a20")
        // Reset to normal in case mode just changed
        thrustZone.setBackgroundColor(bgNormal)
        thrustZone.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { v.setBackgroundColor(bgPressed); gameView.setThrusting(true) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.setBackgroundColor(bgNormal); gameView.setThrusting(false) }
            }
            true
        }
    }

    private fun applyTheme() {
        gameView.applyTheme(ThemeManager.currentTheme(this, GAME_KEY))
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_simple_settings, null)
        val dialog = Dialog(this)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        val themeIndex = ThemeManager.effectiveThemeIndex(this, GAME_KEY)
        view.findViewById<TextView>(R.id.tvCurrentTheme).text =
            Themes.ALL.getOrNull(themeIndex)?.first?.name ?: "Classic"
        view.findViewById<LinearLayout>(R.id.rowTheme).setOnClickListener {
            dialog.dismiss()
            showThemePickerDialog(this, GAME_KEY) { applyTheme() }
        }
        view.findViewById<TextView>(R.id.btnDone).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
