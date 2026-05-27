package com.pocketarcade.games.brickbreaker

import android.app.Dialog
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
import com.pocketarcade.leaderboard.checkAndShowLeaderboard
import com.pocketarcade.leaderboard.showLeaderboardDialog
import com.pocketarcade.showThemePickerDialog
import com.pocketarcade.storage.PrefsManager

class BrickBreakerActivity : AppCompatActivity() {

    private lateinit var bbView: BrickBreakerView
    private lateinit var tvScore: TextView
    private lateinit var tvHighScore: TextView
    private lateinit var tvLevel: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnLeaderboard: TextView
    private val idleHandler = Handler(Looper.getMainLooper())

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
        btnSettings    = findViewById(R.id.btnSettings)
        btnLeaderboard = findViewById(R.id.btnLeaderboard)

        updateHighScore()

        bbView.onScoreChanged = { score ->
            runOnUiThread { tvScore.text = "SCORE: $score" }
        }
        bbView.onLevelChanged = { level ->
            runOnUiThread { tvLevel.text = "LV: $level" }
        }
        bbView.onGameOver = { score ->
            PrefsManager.setHighScore(this, PrefsManager.GAME_BRICKBREAKER, score)
            runOnUiThread { updateHighScore() }
            idleHandler.postDelayed({
                runOnUiThread { checkAndShowLeaderboard(this, PrefsManager.GAME_BRICKBREAKER, score) }
            }, 1500L)
            idleHandler.postDelayed(idleRunnable, 15_000L)
        }
        bbView.onWin = { score ->
            PrefsManager.setHighScore(this, PrefsManager.GAME_BRICKBREAKER, score)
            runOnUiThread { updateHighScore() }
            idleHandler.postDelayed({
                runOnUiThread { checkAndShowLeaderboard(this, PrefsManager.GAME_BRICKBREAKER, score) }
            }, 1500L)
        }

        btnSettings.setOnClickListener { showSettingsDialog() }
        btnLeaderboard.setOnClickListener { showLeaderboardDialog(this, PrefsManager.GAME_BRICKBREAKER) }

        scheduleIdle()
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

    private fun updateHighScore() {
        tvHighScore.text = "High Score: ${PrefsManager.getHighScore(this, PrefsManager.GAME_BRICKBREAKER)}"
    }

    private fun scheduleIdle() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, 15_000L)
    }

    override fun onResume() {
        super.onResume()
        bbView.applyTheme(ThemeManager.currentTheme(this, PrefsManager.GAME_BRICKBREAKER))
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
