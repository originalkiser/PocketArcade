package com.pocketarcade.games.memorymatch

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
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

class MemoryMatchActivity : AppCompatActivity() {

    companion object {
        const val GAME_KEY         = "memorymatch"
        const val EXTRA_DIFFICULTY = "difficulty"
    }

    private lateinit var gameView:    MemoryMatchView
    private lateinit var btnLightMode: TextView
    private lateinit var btnHaptics:   TextView
    private lateinit var btnSound:     TextView
    private lateinit var btnLeaderboard: TextView
    private var gameStartTime = 0L
    private var difficulty    = Difficulty.EASY

    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable {
        if (PrefsManager.isDemoModeEnabled(this) && !gameView.isUserPlaying()) {
            gameView.startDemo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.pocketarcade.SystemColorTheme.applyIfActive(this, this)
        setContentView(R.layout.activity_memorymatch)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        gameView      = findViewById(R.id.memoryMatchView)
        btnLightMode  = findViewById(R.id.btnLightModeMatch)
        btnHaptics    = findViewById(R.id.btnHapticsMatch)
        btnSound      = findViewById(R.id.btnSoundMatch)
        btnLeaderboard = findViewById(R.id.btnLeaderboardMatch)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(android.content.Intent(this, com.pocketarcade.SettingsActivity::class.java))
        }
        btnLightMode.setOnClickListener   { toggleLightMode() }
        btnHaptics.setOnClickListener     { toggleHaptics() }
        btnSound.setOnClickListener       { toggleSound() }
        btnLeaderboard.setOnClickListener { showLeaderboardDialog(this, "${GAME_KEY}_${difficulty.name.lowercase()}") }

        applyTheme()
        updateLightModeButton()
        updateHapticsButton()
        updateSoundButton()

        gameView.onGameStarted = { gameStartTime = System.currentTimeMillis() }
        gameView.onDemoStopped = { runOnUiThread { scheduleIdle() } }

        gameView.onGameWon = { moves, elapsedSecs ->
            val duration = System.currentTimeMillis() - gameStartTime
            val diffKey  = difficulty.name.lowercase()

            val score = when (difficulty) {
                Difficulty.EASY   -> (1000  - moves).coerceAtLeast(1)
                Difficulty.MEDIUM -> (5000  - moves).coerceAtLeast(1)
                Difficulty.HARD   -> (10000 - moves).coerceAtLeast(1)
            }

            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, GAME_KEY, moves, duration, diffKey)
            PrefsManager.setHighScore(this, "${GAME_KEY}_$diffKey", score)
            PrefsManager.setMmBest(this, diffKey, moves, elapsedSecs)

            ScoreSyncManager.recordGameScore(this, "${GAME_KEY}_$diffKey", "_", score)

            val username = PrefsManager.getGlobalUsername(this)
            if (username != null) {
                GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
                    GlobalLeaderboard.submitScore(
                        uid, username, GAME_KEY, score,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        diffKey,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                    GlobalLeaderboard.submitPeriodScore(
                        uid, username, GAME_KEY, score,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        diffKey,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                })
            }

            runOnUiThread {
                checkAndShowLeaderboard(this, GAME_KEY, score, mode = diffKey) {
                    handlePostGameLeaderboards(this, GAME_KEY, diffKey, score)
                }
            }
        }

        // Show difficulty picker in front of the board
        showDifficultyDialog()
    }

    private fun showDifficultyDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_mm_difficulty, null)
        val dialog = Dialog(this)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.setOnCancelListener { finish() }
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        fun pick(diff: Difficulty) {
            difficulty = diff
            gameView.difficulty = diff
            dialog.dismiss()
        }

        view.findViewById<TextView>(R.id.btnDiffBack).setOnClickListener { dialog.dismiss(); finish() }
        view.findViewById<LinearLayout>(R.id.btnEasy).setOnClickListener   { pick(Difficulty.EASY)   }
        view.findViewById<LinearLayout>(R.id.btnMedium).setOnClickListener { pick(Difficulty.MEDIUM) }
        view.findViewById<LinearLayout>(R.id.btnHard).setOnClickListener   { pick(Difficulty.HARD)   }
        dialog.show()
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

    private fun applyTheme() {
        gameView.applyTheme(ThemeManager.currentTheme(this, GAME_KEY))
        ThemeManager.applyWindowBackground(this, GAME_KEY)
    }

    private fun toggleLightMode() {
        ThemeManager.setLightMode(this, !ThemeManager.isLightMode(this))
        updateLightModeButton()
        applyTheme()
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
            if (PrefsManager.isHapticEnabled(this)) getColor(R.color.color_hud_match)
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

    private fun scheduleIdle() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, 15_000L)
    }

    override fun onPause() {
        super.onPause()
        idleHandler.removeCallbacksAndMessages(null)
        gameView.pauseTimer()
    }

    override fun onResume() {
        super.onResume()
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))
        applyTheme()
        updateLightModeButton()
        updateHapticsButton()
        updateSoundButton()
        gameView.resumeTimer()
        scheduleIdle()
    }
}
