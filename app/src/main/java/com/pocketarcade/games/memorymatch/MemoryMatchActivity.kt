package com.pocketarcade.games.memorymatch

import android.app.Dialog
import android.os.Bundle
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
import com.pocketarcade.leaderboard.TimeRange
import com.pocketarcade.leaderboard.checkAndShowLeaderboard
import com.pocketarcade.leaderboard.showGlobalLeaderboardDialog
import com.pocketarcade.showThemePickerDialog
import com.pocketarcade.storage.PrefsManager

class MemoryMatchActivity : AppCompatActivity() {

    companion object {
        const val GAME_KEY         = "memorymatch"
        const val EXTRA_DIFFICULTY = "difficulty"
    }

    private lateinit var gameView:  MemoryMatchView
    private var gameStartTime = 0L
    private var difficulty    = Difficulty.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memorymatch)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        gameView = findViewById(R.id.memoryMatchView)
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }

        applyTheme()

        gameView.onGameStarted = { gameStartTime = System.currentTimeMillis() }

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
                    if (PrefsManager.getGlobalUsername(this) != null) {
                        showGlobalLeaderboardDialog(
                            this, GAME_KEY,
                            mode = diffKey,
                            initialTab = "FRIENDS",
                            initialTimeRange = TimeRange.WEEK
                        )
                    }
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
    }

    override fun onPause() {
        super.onPause()
        gameView.pauseTimer()
    }

    override fun onResume() {
        super.onResume()
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))
        applyTheme()
        gameView.resumeTimer()
    }
}
