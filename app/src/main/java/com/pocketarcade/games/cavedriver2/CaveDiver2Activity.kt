package com.pocketarcade.games.cavedriver2

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.pocketarcade.leaderboard.showLeaderboardDialog
import com.pocketarcade.showThemePickerDialog
import com.pocketarcade.storage.PrefsManager

class CaveDiverActivity : AppCompatActivity() {

    private lateinit var gameView:      CaveDiverView
    private lateinit var thrustZone:    FrameLayout
    private lateinit var btnLightMode:  TextView
    private lateinit var btnHaptics:    TextView
    private lateinit var btnSound:      TextView
    private lateinit var btnLeaderboard: TextView
    private var gameStartTime = 0L

    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable {
        if (PrefsManager.isDemoModeEnabled(this) && !gameView.isUserPlaying()) {
            gameView.startDemo()
        }
    }

    companion object {
        const val GAME_KEY = "cavedriver"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.pocketarcade.SystemColorTheme.applyIfActive(this, this)
        setContentView(R.layout.activity_cavedriver2)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        gameView      = findViewById(R.id.caveDiver2View)
        thrustZone    = findViewById(R.id.thrustZone2)
        btnLightMode  = findViewById(R.id.btnLightModeCave)
        btnHaptics    = findViewById(R.id.btnHapticsCave)
        btnSound      = findViewById(R.id.btnSoundCave)
        btnLeaderboard = findViewById(R.id.btnLeaderboardCave)

        gameView.loadBestScore(PrefsManager.getHighScore(this, GAME_KEY))

        applyTheme()

        findViewById<TextView>(R.id.btnBack2).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnSettings2).setOnClickListener {
            startActivity(android.content.Intent(this, com.pocketarcade.SettingsActivity::class.java))
        }
        // New bottom bar buttons
        btnLightMode.setOnClickListener   { toggleLightMode() }
        btnHaptics.setOnClickListener     { toggleHaptics() }
        btnSound.setOnClickListener       { toggleSound() }
        btnLeaderboard.setOnClickListener { showLeaderboardDialog(this, GAME_KEY) }

        updateLightModeButton()
        updateHapticsButton()
        updateSoundButton()

        gameView.onGameStarted = { gameStartTime = System.currentTimeMillis() }
        gameView.onDemoStopped = { runOnUiThread { scheduleIdle() } }

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
        wireThrustZone()
    }

    override fun onResume() {
        super.onResume()
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))
        applyTheme()
        updateLightModeButton()
        updateHapticsButton()
        updateSoundButton()
        wireThrustZone()
        scheduleIdle()
    }

    override fun onPause() {
        super.onPause()
        idleHandler.removeCallbacksAndMessages(null)
        gameView.setThrusting(false)
    }

    private fun scheduleIdle() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, 15_000L)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun wireThrustZone() {
        val isLight = ThemeManager.isLightMode(this)
        val bgNormal  = if (isLight) getColor(R.color.bg)   else Color.parseColor("#050510")
        val bgPressed = if (isLight) getColor(R.color.card) else Color.parseColor("#0a0a20")
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
        ThemeManager.applyWindowBackground(this, GAME_KEY)
    }

    private fun toggleLightMode() {
        ThemeManager.setLightMode(this, !ThemeManager.isLightMode(this))
        updateLightModeButton()
        applyTheme()
        wireThrustZone()
    }

    private fun updateLightModeButton() {
        btnLightMode.text = if (ThemeManager.isLightMode(this)) "☀" else "🌙"
    }

    private fun toggleHaptics() {
        PrefsManager.setHapticEnabled(this, !PrefsManager.isHapticEnabled(this))
        updateHapticsButton()
    }

    private fun updateHapticsButton() {
        btnHaptics.setTextColor(
            if (PrefsManager.isHapticEnabled(this)) getColor(R.color.color_hud_cave)
            else getColor(R.color.muted)
        )
    }

    private fun toggleSound() {
        PrefsManager.setSoundEnabled(this, !PrefsManager.isSoundEnabled(this))
        updateSoundButton()
    }

    private fun updateSoundButton() {
        btnSound.text = if (PrefsManager.isSoundEnabled(this)) "🔊" else "🔇"
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
