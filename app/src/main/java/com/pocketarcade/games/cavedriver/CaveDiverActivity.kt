package com.pocketarcade.games.cavedriver

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
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

        applyTheme()

        findViewById<TextView>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }

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
        applyTheme()
    }

    private fun applyTheme() {
        cdView.applyTheme(ThemeManager.currentTheme(this, GAME_KEY))
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

    override fun onPause() {
        super.onPause()
        cdView.setThrusting(false)
    }
}
