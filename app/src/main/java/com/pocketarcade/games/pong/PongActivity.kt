package com.pocketarcade.games.pong

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
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

class PongActivity : AppCompatActivity() {

    private lateinit var pongView: PongView
    private lateinit var tvScore: TextView
    private lateinit var tvHighScore: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnLeaderboard: TextView
    private lateinit var btnSound: TextView
    private lateinit var btnLightMode: TextView
    private lateinit var swipeZone: View
    private val idleHandler = Handler(Looper.getMainLooper())
    private var gameStartTime = 0L

    private val idleRunnable = Runnable {
        if (PrefsManager.isDemoModeEnabled(this) && !pongView.isUserPlaying()) {
            pongView.startGame(demo = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pong)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        pongView       = findViewById(R.id.pongView)
        tvScore        = findViewById(R.id.tvScore)
        tvHighScore    = findViewById(R.id.tvHighScore)
        btnSettings    = findViewById(R.id.btnSettings)
        btnLeaderboard = findViewById(R.id.btnLeaderboard)
        btnSound       = findViewById(R.id.btnSound)
        btnLightMode   = findViewById(R.id.btnLightMode)
        swipeZone      = findViewById(R.id.swipeZone)

        updateHighScore()
        showDifficultyDialog(cancelable = false)

        pongView.onScoreUpdate = { p, ai ->
            runOnUiThread { tvScore.text = "P:$p  AI:$ai" }
        }
        pongView.onGameStarted = { gameStartTime = System.currentTimeMillis() }
        pongView.onMatchEnd = { playerWon, playerScore, aiScore ->
            val encodedScore = playerScore * 100 + (99 - aiScore)
            val duration = System.currentTimeMillis() - gameStartTime
            val mode = pongView.difficulty.name.lowercase()
            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, PrefsManager.GAME_PONG, playerScore, duration, mode)
            pongView.readyForRestart = false
            if (playerWon) {
                PrefsManager.setHighScore(this, PrefsManager.GAME_PONG, playerScore)
                PrefsManager.recordPongWin(this)
                runOnUiThread { updateHighScore() }
            }
            idleHandler.postDelayed({
                runOnUiThread {
                    checkAndShowLeaderboard(this, PrefsManager.GAME_PONG, encodedScore, mode = mode) {
                        pongView.readyForRestart = true
                    }
                }
            }, 1500L)
            idleHandler.postDelayed(idleRunnable, 15_000L)
        }

        swipeZone.setOnTouchListener { _, event ->
            pongView.feedExternalTouch(event.action, event.x)
            true
        }

        btnSettings.setOnClickListener { showSettingsDialog() }
        btnLeaderboard.setOnClickListener { showLeaderboardDialog(this, PrefsManager.GAME_PONG) }
        btnSound.setOnClickListener { toggleSound() }
        btnLightMode.setOnClickListener { toggleLightMode() }

        updateSoundButton()
        updateLightModeButton()
    }

    private fun showDifficultyDialog(cancelable: Boolean = true) {
        val view = layoutInflater.inflate(R.layout.dialog_pong_difficulty, null)
        val dialog = Dialog(this)
        dialog.setContentView(view)
        dialog.setCancelable(cancelable)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        fun pick(diff: PongDifficulty) {
            pongView.difficulty = diff
            pongView.startGame(false)
            dialog.dismiss()
        }

        view.findViewById<LinearLayout>(R.id.btnEasy).setOnClickListener { pick(PongDifficulty.EASY) }
        view.findViewById<LinearLayout>(R.id.btnMedium).setOnClickListener { pick(PongDifficulty.MEDIUM) }
        view.findViewById<LinearLayout>(R.id.btnHard).setOnClickListener { pick(PongDifficulty.HARD) }
        dialog.show()
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_pong_settings, null)
        val dialog = Dialog(this)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        val switchSound = view.findViewById<Switch>(R.id.switchSound)
        val tvCurrentTheme = view.findViewById<TextView>(R.id.tvCurrentTheme)
        val tvCurrentDifficulty = view.findViewById<TextView>(R.id.tvCurrentDifficulty)

        switchSound.isChecked = PrefsManager.isSoundEnabled(this)
        switchSound.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setSoundEnabled(this, checked)
            updateSoundButton()
        }

        val themeIndex = ThemeManager.effectiveThemeIndex(this, PrefsManager.GAME_PONG)
        tvCurrentTheme.text = Themes.ALL.getOrNull(themeIndex)?.first?.name ?: "Classic"
        tvCurrentDifficulty.text = pongView.difficulty.name

        view.findViewById<LinearLayout>(R.id.rowTheme).setOnClickListener {
            dialog.dismiss()
            showThemePickerDialog(this, PrefsManager.GAME_PONG) {
                pongView.applyTheme(ThemeManager.currentTheme(this, PrefsManager.GAME_PONG))
            }
        }
        view.findViewById<LinearLayout>(R.id.rowDifficulty).setOnClickListener {
            dialog.dismiss()
            showDifficultyDialog(cancelable = true)
        }
        view.findViewById<TextView>(R.id.btnDone).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun updateHighScore() {
        val hi = PrefsManager.getHighScore(this, PrefsManager.GAME_PONG)
        tvHighScore.text = "Best: $hi"
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
        pongView.applyTheme(ThemeManager.currentTheme(this, PrefsManager.GAME_PONG))
    }

    private fun updateLightModeButton() {
        btnLightMode.text = if (ThemeManager.isLightMode(this)) "☀" else "🌙"
    }

    override fun onResume() {
        super.onResume()
        pongView.applyTheme(ThemeManager.currentTheme(this, PrefsManager.GAME_PONG))
        ThemeManager.applyWindowBackground(this, PrefsManager.GAME_PONG)
        updateSoundButton()
        updateLightModeButton()
        idleHandler.postDelayed(idleRunnable, 15_000L)
    }

    override fun onPause() {
        super.onPause()
        idleHandler.removeCallbacksAndMessages(null)
    }
}
