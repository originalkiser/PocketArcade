package com.pocketarcade.games.blockpop

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

class BlockDropActivity : AppCompatActivity() {

    private lateinit var gameView: BlockDropView
    private var gameStartTime = 0L
    private var difficulty = BlockDropDifficulty.MEDIUM

    companion object {
        const val GAME_KEY = "blockdrop"

        /** Per-difficulty high score key. */
        fun scoreKey(diff: BlockDropDifficulty) = "${GAME_KEY}_${diff.key}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_pop)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        gameView = findViewById(R.id.blockPopView)

        applyTheme()

        findViewById<TextView>(R.id.btnBackBlockPop).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnSettingsBlockDrop).setOnClickListener { showSettingsDialog() }

        gameView.onGameStarted = {
            gameStartTime = System.currentTimeMillis()
        }

        // STUCK — record stats but do NOT submit to fewest-moves leaderboard
        gameView.onGameOver = { moves ->
            val duration = System.currentTimeMillis() - gameStartTime
            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, GAME_KEY, moves, duration, difficulty.key)
        }

        // WON — fewest-moves leaderboard
        gameView.onGameWon = { moves ->
            val duration = System.currentTimeMillis() - gameStartTime
            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, GAME_KEY, moves, duration, difficulty.key)
            PrefsManager.setBdBest(this, difficulty.key, moves)

            val leaderScore = gameView.movesToScore(moves)
            val diffKey     = difficulty.key
            PrefsManager.setHighScore(this, scoreKey(difficulty), leaderScore)
            ScoreSyncManager.recordGameScore(this, scoreKey(difficulty), "_", leaderScore)

            val username = PrefsManager.getGlobalUsername(this)
            if (username != null) {
                GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
                    GlobalLeaderboard.submitScore(
                        uid, username, GAME_KEY, leaderScore,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        diffKey,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                    GlobalLeaderboard.submitPeriodScore(
                        uid, username, GAME_KEY, leaderScore,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        diffKey,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                })
            }

            runOnUiThread {
                checkAndShowLeaderboard(this, scoreKey(difficulty), leaderScore) {
                    if (PrefsManager.getGlobalUsername(this) != null) {
                        showGlobalLeaderboardDialog(
                            this, GAME_KEY,
                            initialTab = "FRIENDS",
                            initialTimeRange = TimeRange.WEEK
                        )
                    }
                }
            }
        }

        // Load best score (medium difficulty by default until dialog picks one)
        gameView.loadBestScore(PrefsManager.getHighScore(this, scoreKey(BlockDropDifficulty.MEDIUM)))

        showDifficultyDialog()
    }

    private fun showDifficultyDialog() {
        val view   = layoutInflater.inflate(R.layout.dialog_bd_difficulty, null)
        val dialog = Dialog(this)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.setOnCancelListener { finish() }
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        fun pick(diff: BlockDropDifficulty) {
            difficulty = diff
            gameView.loadBestScore(PrefsManager.getHighScore(this, scoreKey(diff)))
            gameView.difficulty = diff   // triggers resetForNewGame via setter
            dialog.dismiss()
        }

        view.findViewById<TextView>(R.id.btnDiffBack).setOnClickListener { dialog.dismiss(); finish() }
        view.findViewById<LinearLayout>(R.id.btnEasy).setOnClickListener   { pick(BlockDropDifficulty.EASY)   }
        view.findViewById<LinearLayout>(R.id.btnMedium).setOnClickListener { pick(BlockDropDifficulty.MEDIUM) }
        view.findViewById<LinearLayout>(R.id.btnHard).setOnClickListener   { pick(BlockDropDifficulty.HARD)   }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))
        applyTheme()
        gameView.onResume()
    }

    override fun onPause() {
        super.onPause()
        gameView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.onDestroy()
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
