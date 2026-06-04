package com.pocketarcade.games.cavedriver

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.R
import com.pocketarcade.ads.AdManager
import com.pocketarcade.leaderboard.checkAndShowLeaderboard
import com.pocketarcade.storage.PrefsManager

class CaveDiverActivity : AppCompatActivity() {

    private lateinit var cdView:  CaveDiverView
    private lateinit var btnBack: TextView

    private var gameStartTime = 0L

    companion object {
        const val GAME_KEY = "cavedriver"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cavedriver)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        cdView  = findViewById(R.id.caveDiverView)
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        // Seed the view with the persisted best score so it shows on the dead screen
        cdView.loadBestScore(PrefsManager.getHighScore(this, GAME_KEY))

        cdView.onGameStarted = {
            gameStartTime = System.currentTimeMillis()
        }

        cdView.onGameOver = { score ->
            val duration = System.currentTimeMillis() - gameStartTime
            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, GAME_KEY, score, duration)
            PrefsManager.setHighScore(this, GAME_KEY, score)

            runOnUiThread {
                checkAndShowLeaderboard(this, GAME_KEY, score) {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
