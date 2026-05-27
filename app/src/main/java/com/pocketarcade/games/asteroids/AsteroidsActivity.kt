package com.pocketarcade.games.asteroids

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.R
import com.pocketarcade.ThemeManager
import com.pocketarcade.ads.AdManager
import com.pocketarcade.leaderboard.checkAndShowLeaderboard
import com.pocketarcade.leaderboard.showLeaderboardDialog
import com.pocketarcade.showThemePickerDialog
import com.pocketarcade.storage.PrefsManager

class AsteroidsActivity : AppCompatActivity() {

    private lateinit var astView: AsteroidsView
    private lateinit var tvScore: TextView
    private lateinit var tvHighScore: TextView
    private lateinit var btnTheme: TextView
    private lateinit var btnLeaderboard: TextView
    private val idleHandler = Handler(Looper.getMainLooper())

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
        tvScore        = findViewById(R.id.tvScore)
        tvHighScore    = findViewById(R.id.tvHighScore)
        btnTheme       = findViewById(R.id.btnTheme)
        btnLeaderboard = findViewById(R.id.btnLeaderboard)

        updateHighScore()

        astView.onScoreChanged = { score ->
            runOnUiThread { tvScore.text = "SCORE: $score" }
        }
        astView.onGameOver = { score ->
            PrefsManager.setHighScore(this, PrefsManager.GAME_ASTEROIDS, score)
            runOnUiThread { updateHighScore() }
            idleHandler.postDelayed({
                runOnUiThread { checkAndShowLeaderboard(this, PrefsManager.GAME_ASTEROIDS, score) }
            }, 1500L)
            idleHandler.postDelayed(idleRunnable, 15_000L)
        }

        btnTheme.setOnClickListener {
            showThemePickerDialog(this, PrefsManager.GAME_ASTEROIDS) {
                astView.applyTheme(ThemeManager.currentTheme(this, PrefsManager.GAME_ASTEROIDS))
            }
        }
        btnLeaderboard.setOnClickListener {
            showLeaderboardDialog(this, PrefsManager.GAME_ASTEROIDS)
        }

        scheduleIdle()
    }

    private fun updateHighScore() {
        tvHighScore.text = "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_ASTEROIDS)}"
    }

    private fun scheduleIdle() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, 15_000L)
    }

    override fun onResume() {
        super.onResume()
        astView.applyTheme(ThemeManager.currentTheme(this, PrefsManager.GAME_ASTEROIDS))
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
