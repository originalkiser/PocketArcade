package com.pocketarcade.games.asteroids

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.R
import com.pocketarcade.ThemeManager
import com.pocketarcade.Themes
import com.pocketarcade.ads.AdManager
import com.pocketarcade.leaderboard.checkAndShowLeaderboard
import com.pocketarcade.leaderboard.showLeaderboardDialog
import com.pocketarcade.showThemePickerDialog
import com.pocketarcade.storage.PrefsManager

class AsteroidsActivity : AppCompatActivity() {

    private lateinit var astView: AsteroidsView
    private lateinit var controlsPanel: AsteroidsControlsView
    private lateinit var tvScore: TextView
    private lateinit var tvHighScore: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnAutoFire: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnLeaderboard: TextView
    private lateinit var btnSound: TextView
    private lateinit var btnLightMode: TextView
    private val idleHandler = Handler(Looper.getMainLooper())
    private var gameStartTime = 0L

    private val idleRunnable = Runnable {
        if (PrefsManager.isDemoModeEnabled(this) && !astView.isUserPlaying()) {
            astView.startGame(demo = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asteroids)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        astView        = findViewById(R.id.asteroidsView)
        controlsPanel  = findViewById(R.id.controlsPanel)
        tvScore        = findViewById(R.id.tvScore)
        tvHighScore    = findViewById(R.id.tvHighScore)
        btnBack        = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }
        btnAutoFire    = findViewById(R.id.btnAutoFire)
        btnSettings    = findViewById(R.id.btnSettings)
        btnLeaderboard = findViewById(R.id.btnLeaderboard)
        btnSound       = findViewById(R.id.btnSound)
        btnLightMode   = findViewById(R.id.btnLightMode)

        updateHighScore()

        controlsPanel.onJoystick = { dx, dy -> astView.setJoystick(dx, dy) }
        controlsPanel.onFire = { pressed -> astView.setFireInput(pressed) }

        astView.onScoreChanged = { score ->
            runOnUiThread { tvScore.text = "SCORE: $score" }
        }
        astView.onGameStarted = { gameStartTime = System.currentTimeMillis() }
        astView.onGameOver = { score ->
            val duration = System.currentTimeMillis() - gameStartTime
            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, PrefsManager.GAME_ASTEROIDS, score, duration)
            astView.readyForRestart = false
            PrefsManager.setHighScore(this, PrefsManager.GAME_ASTEROIDS, score)
            runOnUiThread { updateHighScore() }
            idleHandler.postDelayed({
                runOnUiThread {
                    checkAndShowLeaderboard(this, PrefsManager.GAME_ASTEROIDS, score) {
                        astView.readyForRestart = true
                    }
                }
            }, 1500L)
            idleHandler.postDelayed(idleRunnable, 15_000L)
        }

        btnAutoFire.setOnClickListener { toggleAutoFire() }
        btnSettings.setOnClickListener { showSettingsDialog() }
        btnLeaderboard.setOnClickListener { showLeaderboardDialog(this, PrefsManager.GAME_ASTEROIDS) }
        btnSound.setOnClickListener { toggleSound() }
        btnLightMode.setOnClickListener { toggleLightMode() }

        astView.autoFire = true
        updateAutoFireButton()
        updateSoundButton()
        updateLightModeButton()
        scheduleIdle()
    }

    private fun toggleAutoFire() {
        astView.autoFire = !astView.autoFire
        updateAutoFireButton()
    }

    private fun updateAutoFireButton() {
        val on = astView.autoFire
        btnAutoFire.setTextColor(if (on) getColor(R.color.accent_cyan) else getColor(R.color.muted))
        controlsPanel.autoFireEnabled = on
        controlsPanel.invalidate()
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_asteroids_settings, null)
        val dialog = Dialog(this)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        val switchSound = view.findViewById<Switch>(R.id.switchSound)
        val tvCurrentTheme = view.findViewById<TextView>(R.id.tvCurrentTheme)

        switchSound.isChecked = PrefsManager.isSoundEnabled(this)
        switchSound.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setSoundEnabled(this, checked)
            updateSoundButton()
        }

        val themeIndex = ThemeManager.effectiveThemeIndex(this, PrefsManager.GAME_ASTEROIDS)
        tvCurrentTheme.text = Themes.ALL.getOrNull(themeIndex)?.first?.name ?: "Classic"

        view.findViewById<LinearLayout>(R.id.rowTheme).setOnClickListener {
            dialog.dismiss()
            showThemePickerDialog(this, PrefsManager.GAME_ASTEROIDS) {
                applyCurrentTheme()
            }
        }
        view.findViewById<TextView>(R.id.btnDone).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun applyCurrentTheme() {
        val theme = ThemeManager.currentTheme(this, PrefsManager.GAME_ASTEROIDS)
        astView.applyTheme(theme)
        controlsPanel.applyTheme(theme.accent, theme.muted)
        ThemeManager.applyWindowBackground(this, PrefsManager.GAME_ASTEROIDS)
    }

    private fun updateHighScore() {
        tvHighScore.text = "Best: ${PrefsManager.getHighScore(this, PrefsManager.GAME_ASTEROIDS)}"
    }

    private fun toggleSound() {
        PrefsManager.setSoundEnabled(this, !PrefsManager.isSoundEnabled(this))
        updateSoundButton()
    }

    private fun updateSoundButton() {
        btnSound.text = if (PrefsManager.isSoundEnabled(this)) "🔊" else "🔇"
    }

    private fun toggleLightMode() {
        ThemeManager.setLightMode(this, !ThemeManager.isLightMode(this))
        updateLightModeButton()
        applyCurrentTheme()
    }

    private fun updateLightModeButton() {
        btnLightMode.text = if (ThemeManager.isLightMode(this)) "☀" else "🌙"
    }

    private fun scheduleIdle() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, 15_000L)
    }

    override fun onResume() {
        super.onResume()
        applyCurrentTheme()
        updateSoundButton()
        updateLightModeButton()
        scheduleIdle()
    }

    override fun onPause() {
        super.onPause()
        idleHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        idleHandler.removeCallbacksAndMessages(null)
    }
}
