package com.pocketarcade.games.memorymatch

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.R
import com.pocketarcade.ScoreSyncManager
import com.pocketarcade.ads.AdManager
import com.pocketarcade.leaderboard.GlobalLeaderboard
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

        val diffName = intent.getStringExtra(EXTRA_DIFFICULTY) ?: Difficulty.EASY.name
        difficulty   = runCatching { Difficulty.valueOf(diffName) }.getOrDefault(Difficulty.EASY)
        gameView.difficulty = difficulty

        gameView.onGameStarted = { gameStartTime = System.currentTimeMillis() }

        gameView.onGameWon = { moves, elapsedSecs ->
            val duration = System.currentTimeMillis() - gameStartTime
            val diffKey  = difficulty.name.lowercase()

            // Inverted score: fewer moves = higher value (works with setHighScore's max logic)
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
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.pauseTimer()
    }

    override fun onResume() {
        super.onResume()
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))
        gameView.resumeTimer()
    }
}
