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
        const val GAME_KEY = "memorymatch"
    }

    private lateinit var gameView: MemoryMatchView
    private var gameStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memorymatch)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        gameView = findViewById(R.id.memoryMatchView)
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        gameView.onGameStarted = {
            gameStartTime = System.currentTimeMillis()
        }

        gameView.onGameWon = { moves ->
            val duration = System.currentTimeMillis() - gameStartTime
            // Encode as inverted score so higher stored value = fewer moves (better).
            // Range: moves=8 (perfect) → 992; moves=32+ → 968 or lower.
            val score = (1000 - moves).coerceAtLeast(1)

            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, GAME_KEY, moves, duration)
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
