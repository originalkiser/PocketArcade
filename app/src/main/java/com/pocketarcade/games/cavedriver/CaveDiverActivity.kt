package com.pocketarcade.games.cavedriver

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.R
import com.pocketarcade.ScoreSyncManager
import com.pocketarcade.ads.AdManager
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.leaderboard.TimeRange
import com.pocketarcade.leaderboard.checkAndShowLeaderboard
import com.pocketarcade.leaderboard.showGlobalLeaderboardDialog
import com.pocketarcade.storage.PrefsManager

class CaveDiverActivity : AppCompatActivity() {

    private lateinit var cdView:     CaveDiverView
    private lateinit var thrustZone: FrameLayout
    private var gameStartTime = 0L

    companion object {
        const val GAME_KEY = "cavedriver"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cavedriver)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        cdView     = findViewById(R.id.caveDiverView)
        thrustZone = findViewById(R.id.thrustZone)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        cdView.loadBestScore(PrefsManager.getHighScore(this, GAME_KEY))

        cdView.onGameStarted = {
            gameStartTime = System.currentTimeMillis()
        }

        cdView.onGameOver = { score ->
            val duration = System.currentTimeMillis() - gameStartTime
            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, GAME_KEY, score, duration)
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

            runOnUiThread {
                checkAndShowLeaderboard(this, GAME_KEY, score) {
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

        // Control zone: hold here to thrust (mirrors in-canvas touch)
        thrustZone.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.setBackgroundColor(Color.parseColor("#101128"))
                    cdView.setThrusting(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundColor(Color.parseColor("#070715"))
                    cdView.setThrusting(false)
                }
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))
    }

    override fun onPause() {
        super.onPause()
        cdView.setThrusting(false)
    }
}
