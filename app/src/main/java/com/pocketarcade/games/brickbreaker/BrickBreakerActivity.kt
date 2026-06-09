package com.pocketarcade.games.brickbreaker

import android.app.Dialog
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.R
import com.pocketarcade.ThemeManager
import com.pocketarcade.Themes
import com.pocketarcade.ads.AdManager
import com.pocketarcade.ScoreSyncManager
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.leaderboard.checkAndShowLeaderboard
import com.pocketarcade.leaderboard.handlePostGameLeaderboards
import com.pocketarcade.leaderboard.showLeaderboardDialog
import com.pocketarcade.showThemePickerDialog
import com.pocketarcade.storage.PrefsManager

class BrickBreakerActivity : AppCompatActivity() {

    private lateinit var bbView: BrickBreakerView
    private lateinit var tvScore: TextView
    private lateinit var tvHighScore: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvLives: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnLeaderboard: TextView
    private lateinit var btnSound: TextView
    private lateinit var btnLightMode: TextView
    private lateinit var btnStartGame: TextView
    private lateinit var swipeZone: View
    private val idleHandler = Handler(Looper.getMainLooper())
    private var gameStartTime = 0L
    private var currentLevel = 1

    private val idleRunnable = Runnable {
        if (PrefsManager.isDemoModeEnabled(this) && !bbView.isUserPlaying()) {
            bbView.startGame(demo = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_brickbreaker)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        bbView         = findViewById(R.id.brickBreakerView)
        tvScore        = findViewById(R.id.tvScore)
        tvHighScore    = findViewById(R.id.tvHighScore)
        tvLevel        = findViewById(R.id.tvLevel)
        tvLives        = findViewById(R.id.tvLives)
        btnBack        = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }
        btnSettings    = findViewById(R.id.btnSettings)
        btnLeaderboard = findViewById(R.id.btnLeaderboard)
        btnSound       = findViewById(R.id.btnSound)
        btnLightMode   = findViewById(R.id.btnLightMode)
        swipeZone      = findViewById(R.id.swipeZone)
        btnStartGame   = findViewById(R.id.btnStartGame)

        btnStartGame.setOnClickListener {
            btnStartGame.visibility = View.GONE
            bbView.startGame(demo = false)
        }

        updateHighScore()

        bbView.onScoreChanged = { score ->
            runOnUiThread { tvScore.text = "SCORE: $score" }
        }
        bbView.onLivesChanged = { lives ->
            runOnUiThread { tvLives.text = "♥".repeat(lives.coerceAtLeast(0)) }
        }
        bbView.onLevelChanged = { level ->
            currentLevel = level
            runOnUiThread { updateLevelDisplay() }
        }
        bbView.onGameStarted = {
            gameStartTime = System.currentTimeMillis()
            runOnUiThread { btnStartGame.visibility = View.GONE }
        }
        bbView.onGameOver = { score ->
            val duration = System.currentTimeMillis() - gameStartTime
            val mode = bbView.difficulty.name.lowercase()
            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, PrefsManager.GAME_BRICKBREAKER, score, duration, mode)
            bbView.readyForRestart = false
            PrefsManager.setHighScore(this, PrefsManager.GAME_BRICKBREAKER, score)
            runOnUiThread { updateHighScore() }

            // Record local best for offline sync, then push to both leaderboards.
            ScoreSyncManager.recordGameScore(this, PrefsManager.GAME_BRICKBREAKER, mode, score)
            val username = PrefsManager.getGlobalUsername(this)
            if (username != null) {
                GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
                    GlobalLeaderboard.submitScore(
                        uid, username, PrefsManager.GAME_BRICKBREAKER, score,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        mode,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                    GlobalLeaderboard.submitPeriodScore(
                        uid, username, PrefsManager.GAME_BRICKBREAKER, score,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        mode,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                })
            }

            idleHandler.postDelayed({
                runOnUiThread {
                    checkAndShowLeaderboard(this, PrefsManager.GAME_BRICKBREAKER, score, mode = mode) {
                        bbView.readyForRestart = true
                        runOnUiThread { btnStartGame.visibility = View.VISIBLE }
                        handlePostGameLeaderboards(this, PrefsManager.GAME_BRICKBREAKER, mode, score)
                    }
                }
            }, 1500L)
            idleHandler.postDelayed(idleRunnable, 15_000L)
        }
        bbView.onWin = { score ->
            val duration = System.currentTimeMillis() - gameStartTime
            val mode = bbView.difficulty.name.lowercase()
            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, PrefsManager.GAME_BRICKBREAKER, score, duration, mode)
            bbView.readyForRestart = false
            PrefsManager.setHighScore(this, PrefsManager.GAME_BRICKBREAKER, score)
            runOnUiThread { updateHighScore() }

            // Record local best for offline sync, then push to both leaderboards.
            ScoreSyncManager.recordGameScore(this, PrefsManager.GAME_BRICKBREAKER, mode, score)
            val username = PrefsManager.getGlobalUsername(this)
            if (username != null) {
                GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
                    GlobalLeaderboard.submitScore(
                        uid, username, PrefsManager.GAME_BRICKBREAKER, score,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        mode,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                    GlobalLeaderboard.submitPeriodScore(
                        uid, username, PrefsManager.GAME_BRICKBREAKER, score,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        mode,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                })
            }

            idleHandler.postDelayed({
                runOnUiThread {
                    checkAndShowLeaderboard(this, PrefsManager.GAME_BRICKBREAKER, score, mode = mode) {
                        bbView.readyForRestart = true
                        runOnUiThread { btnStartGame.visibility = View.VISIBLE }
                        handlePostGameLeaderboards(this, PrefsManager.GAME_BRICKBREAKER, mode, score)
                    }
                }
            }, 1500L)
        }

        swipeZone.setOnTouchListener { _, event ->
            bbView.feedTouch(event.action, event.x, event.y)
            true
        }

        btnSettings.setOnClickListener { startActivity(android.content.Intent(this, com.pocketarcade.SettingsActivity::class.java)) }
        btnLeaderboard.setOnClickListener { showLeaderboardDialog(this, PrefsManager.GAME_BRICKBREAKER) }
        btnSound.setOnClickListener { toggleSound() }
        btnLightMode.setOnClickListener { toggleLightMode() }

        bbView.difficulty = indexToDifficulty(PrefsManager.getBBDifficulty(this))
        updateSoundButton()
        updateLightModeButton()
        scheduleIdle()
        showDifficultyDialog(goBackOnCancel = true)
    }

    private fun showDifficultyDialog(goBackOnCancel: Boolean = false) {
        val view = layoutInflater.inflate(R.layout.dialog_bb_difficulty, null)
        val dialog = android.app.Dialog(this)
        dialog.setContentView(view)
        dialog.setCancelable(goBackOnCancel)
        if (goBackOnCancel) dialog.setOnCancelListener { finish() }
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        }

        fun pick(diff: BBDifficulty, index: Int) {
            PrefsManager.setBBDifficulty(this, index)
            bbView.difficulty = diff
            dialog.dismiss()
            // Show the START GAME button floating over the game board.
            btnStartGame.visibility = View.VISIBLE
        }

        view.findViewById<TextView>(R.id.btnDiffBack).setOnClickListener { dialog.dismiss(); finish() }
        view.findViewById<android.widget.LinearLayout>(R.id.btnEasy).setOnClickListener { pick(BBDifficulty.EASY, 0) }
        view.findViewById<android.widget.LinearLayout>(R.id.btnMedium).setOnClickListener { pick(BBDifficulty.MEDIUM, 1) }
        view.findViewById<android.widget.LinearLayout>(R.id.btnHard).setOnClickListener { pick(BBDifficulty.HARD, 2) }
        dialog.show()
    }

    private fun indexToDifficulty(i: Int) = when (i) {
        0 -> BBDifficulty.EASY
        2 -> BBDifficulty.HARD
        else -> BBDifficulty.MEDIUM
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_bb_settings, null)
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

        // Difficulty buttons
        val btnEasy   = view.findViewById<TextView>(R.id.btnDiffEasy)
        val btnMedium = view.findViewById<TextView>(R.id.btnDiffMedium)
        val btnHard   = view.findViewById<TextView>(R.id.btnDiffHard)

        fun refreshDiff() {
            val d = PrefsManager.getBBDifficulty(this)
            val active   = getColor(R.color.accent_yellow)
            val inactive = getColor(R.color.muted)
            btnEasy.setTextColor(if (d == 0) active else inactive)
            btnMedium.setTextColor(if (d == 1) active else inactive)
            btnHard.setTextColor(if (d == 2) active else inactive)
        }
        refreshDiff()

        if (bbView.isUserPlaying()) {
            listOf(btnEasy, btnMedium, btnHard).forEach { btn ->
                btn.alpha = 0.4f
                btn.isClickable = false
            }
        } else {
            btnEasy.setOnClickListener {
                PrefsManager.setBBDifficulty(this, 0)
                bbView.difficulty = BBDifficulty.EASY
                refreshDiff()
            }
            btnMedium.setOnClickListener {
                PrefsManager.setBBDifficulty(this, 1)
                bbView.difficulty = BBDifficulty.MEDIUM
                refreshDiff()
            }
            btnHard.setOnClickListener {
                PrefsManager.setBBDifficulty(this, 2)
                bbView.difficulty = BBDifficulty.HARD
                refreshDiff()
            }
        }

        val themeIndex = ThemeManager.effectiveThemeIndex(this, PrefsManager.GAME_BRICKBREAKER)
        tvCurrentTheme.text = Themes.ALL.getOrNull(themeIndex)?.first?.name ?: "Classic"

        view.findViewById<LinearLayout>(R.id.rowTheme).setOnClickListener {
            dialog.dismiss()
            showThemePickerDialog(this, PrefsManager.GAME_BRICKBREAKER) {
                bbView.applyTheme(ThemeManager.currentTheme(this, PrefsManager.GAME_BRICKBREAKER))
            }
        }
        view.findViewById<TextView>(R.id.btnDone).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun updateLevelDisplay() {
        val diffPrefix = when (bbView.difficulty) {
            BBDifficulty.EASY   -> "E"
            BBDifficulty.MEDIUM -> "M"
            BBDifficulty.HARD   -> "H"
        }
        tvLevel.text = "$diffPrefix·LV:$currentLevel"
    }

    private fun updateHighScore() {
        tvHighScore.text = "HIGH SCORE: ${PrefsManager.getHighScore(this, PrefsManager.GAME_BRICKBREAKER)}"
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
        bbView.applyTheme(ThemeManager.currentTheme(this, PrefsManager.GAME_BRICKBREAKER))
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
        bbView.applyTheme(ThemeManager.currentTheme(this, PrefsManager.GAME_BRICKBREAKER))
        ThemeManager.applyWindowBackground(this, PrefsManager.GAME_BRICKBREAKER)
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
