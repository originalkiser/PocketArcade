package com.pocketarcade.games.snake

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import com.pocketarcade.splitOvalDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pocketarcade.HintManager
import com.pocketarcade.R
import com.pocketarcade.ScoreSyncManager
import com.pocketarcade.ThemeManager
import com.pocketarcade.Themes
import com.pocketarcade.ads.AdManager
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.leaderboard.checkAndShowLeaderboard
import com.pocketarcade.leaderboard.handlePostGameLeaderboards
import com.pocketarcade.leaderboard.showLeaderboardDialog
import com.pocketarcade.storage.PrefsManager
import com.pocketarcade.withAlpha

class SnakeActivity : AppCompatActivity() {

    private lateinit var snakeView: SnakeView
    private lateinit var tvScore: TextView
    private lateinit var tvHighScore: TextView
    private lateinit var btnBack: TextView
    private lateinit var controlZone: FrameLayout
    private lateinit var swipeZoneContainer: FrameLayout
    private lateinit var dpadZone: SnakeDpadZoneView
    private lateinit var btnRestart: View
    private lateinit var btnSwipeMode: TextView
    private lateinit var btnDpadMode: TextView
    private lateinit var btnLightMode: TextView
    private lateinit var btnHaptics: TextView
    private lateinit var btnSound: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnLeaderboard: TextView

    private var isDpadMode = false
    private var gameStartTime = 0L
    private var fruitsThisGame = 0
    private var consecutiveLowScores = 0  // resets when score >= 15; not persisted

    private var swipeSensitivity = 2

    private val prefs by lazy { getPreferences(MODE_PRIVATE) }

    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleTimeout = 15_000L
    private val idleRunnable = Runnable {
        if (PrefsManager.isDemoModeEnabled(this) && !snakeView.isUserPlaying()) {
            btnRestart.visibility = View.GONE
            snakeView.startDemo()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.pocketarcade.SystemColorTheme.applyIfActive(this, this)
        setContentView(R.layout.activity_snake)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        snakeView          = findViewById(R.id.snakeView)
        tvScore            = findViewById(R.id.tvScore)
        tvHighScore        = findViewById(R.id.tvHighScore)
        btnBack            = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }
        controlZone        = findViewById(R.id.controlZone)
        swipeZoneContainer = findViewById(R.id.swipeZoneContainer)
        dpadZone           = findViewById(R.id.dpadZone)
        btnRestart         = findViewById(R.id.btnRestart)
        btnSwipeMode       = findViewById(R.id.btnSwipeMode)
        btnDpadMode        = findViewById(R.id.btnDpadMode)
        btnLightMode       = findViewById(R.id.btnLightMode)
        btnHaptics         = findViewById(R.id.btnHaptics)
        btnSound           = findViewById(R.id.btnSound)
        btnSettings        = findViewById(R.id.btnSettings)
        btnLeaderboard     = findViewById(R.id.btnLeaderboard)

        loadSettings()
        updateHighScore()
        applyThemeToUi()

        snakeView.onGameStarted  = {
            gameStartTime = System.currentTimeMillis()
            fruitsThisGame = 0
            runOnUiThread { btnRestart.visibility = View.GONE }
        }
        snakeView.onFruitEaten   = { fruitsThisGame++ }
        snakeView.onDemoStopped  = { runOnUiThread { btnRestart.visibility = View.VISIBLE } }
        snakeView.onScoreChanged = { score -> runOnUiThread { tvScore.text = "SCORE: $score" } }
        snakeView.onGameOver = { score ->
            val duration = System.currentTimeMillis() - gameStartTime
            PrefsManager.recordGamePlayed(this)
            PrefsManager.recordGameStat(this, PrefsManager.GAME_SNAKE, score, duration)
            PrefsManager.addSnakeFruits(this, fruitsThisGame)
            PrefsManager.setHighScore(this, PrefsManager.GAME_SNAKE, score)
            runOnUiThread { updateHighScore() }

            // Track consecutive low scores for the control-hint system.
            if (score < 15) consecutiveLowScores++ else consecutiveLowScores = 0

            // Record local best for offline sync, then push to both leaderboards.
            ScoreSyncManager.recordGameScore(this, PrefsManager.GAME_SNAKE, "_", score)
            val username = PrefsManager.getGlobalUsername(this)
            if (username != null) {
                GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
                    GlobalLeaderboard.submitScore(
                        uid, username, PrefsManager.GAME_SNAKE, score,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        null,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                    GlobalLeaderboard.submitPeriodScore(
                        uid, username, PrefsManager.GAME_SNAKE, score,
                        PrefsManager.getGlobalCountry(this),
                        PrefsManager.getGlobalState(this),
                        null,
                        PrefsManager.getAvatarIndex(this),
                        PrefsManager.getAvatarColor(this)
                    )
                })
            }

            idleHandler.postDelayed({
                runOnUiThread {
                    checkAndShowLeaderboard(this, PrefsManager.GAME_SNAKE, score) {
                        btnRestart.visibility = View.VISIBLE
                        handlePostGameLeaderboards(this, PrefsManager.GAME_SNAKE, null, score)
                    }
                    checkControlHint()
                }
            }, 1500L)
            scheduleIdle()
        }

        btnRestart.setOnClickListener {
            btnRestart.visibility = View.GONE
            idleHandler.removeCallbacks(idleRunnable)
            snakeView.startGame()
        }

        // Wire the triangular zone view to the Snake view's d-pad inputs
        dpadZone.onUp    = { snakeView.dpadUp()    }
        dpadZone.onDown  = { snakeView.dpadDown()  }
        dpadZone.onLeft  = { snakeView.dpadLeft()  }
        dpadZone.onRight = { snakeView.dpadRight() }

        btnSwipeMode.setOnClickListener  { setDpadMode(false) }
        btnDpadMode.setOnClickListener   { setDpadMode(true) }
        btnLightMode.setOnClickListener  { toggleLightMode() }
        btnHaptics.setOnClickListener    { toggleHaptics() }
        btnSound.setOnClickListener      { toggleSound() }
        btnSettings.setOnClickListener   { startActivity(android.content.Intent(this, com.pocketarcade.SettingsActivity::class.java)) }
        btnLeaderboard.setOnClickListener { showLeaderboardDialog(this, PrefsManager.GAME_SNAKE) }

        updateSoundButton()
        updateHapticsButton()

        swipeZoneContainer.setOnTouchListener { _, event ->
            snakeView.handleExternalTouch(event)
        }

        val controlBar = findViewById<LinearLayout>(R.id.controlBar)
        ViewCompat.setOnApplyWindowInsetsListener(controlBar) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(0, 0, 0, navBottom)
            insets
        }

        applyControlBarState()
        scheduleIdle()

        HintManager.showToastIfNeeded(this,
            "You can adjust controls in Settings.",
            PrefsManager.HINT_SNAKE_CONTROLS)
    }

    override fun onResume() {
        super.onResume()
        applyThemeToUi()
        updateSoundButton()
        btnRestart.visibility = if (snakeView.isRunning()) View.GONE else View.VISIBLE
        scheduleIdle()
    }

    override fun onPause() {
        super.onPause()
        idleHandler.removeCallbacks(idleRunnable)
        snakeView.stopGame()
    }

    override fun onDestroy() {
        super.onDestroy()
        idleHandler.removeCallbacksAndMessages(null)
    }

    // ── Theme ──────────────────────────────────────────────────────────────────

    private fun applyThemeToUi() {
        val theme = ThemeManager.currentTheme(this, PrefsManager.GAME_SNAKE)
        snakeView.applyTheme(theme)
        ThemeManager.applyWindowBackground(this, PrefsManager.GAME_SNAKE)

        val d = resources.displayMetrics.density
        val gd = GradientDrawable()
        gd.cornerRadius = 8f * d
        gd.setColor(if (theme.swipeZoneBg != 0) theme.swipeZoneBg else Color.TRANSPARENT)
        gd.setStroke((1 * d).toInt(), theme.accent, 6f * d, 3f * d)
        swipeZoneContainer.background = gd

        dpadZone.applyTheme(theme.accent, theme.text)

        btnLightMode.text = if (ThemeManager.isLightMode(this)) "☀" else "🌙"
    }

    private fun toggleLightMode() {
        ThemeManager.setLightModeInPlace(this, !ThemeManager.isLightMode(this))
        applyThemeToUi()
    }

    private fun toggleSound() {
        PrefsManager.setSoundEnabled(this, !PrefsManager.isSoundEnabled(this))
        updateSoundButton()
    }

    private fun updateSoundButton() {
        btnSound.text = if (PrefsManager.isSoundEnabled(this)) "🔊" else "🔇"
    }

    private fun toggleHaptics() {
        PrefsManager.setHapticEnabled(this, !PrefsManager.isHapticEnabled(this))
        updateHapticsButton()
    }

    private fun updateHapticsButton() {
        btnHaptics.setTextColor(
            if (PrefsManager.isHapticEnabled(this)) getColor(R.color.accent_blue)
            else getColor(R.color.muted)
        )
    }

    // ── Settings persistence ───────────────────────────────────────────────────

    private fun loadSettings() {
        isDpadMode       = prefs.getBoolean("control_dpad", false)
        swipeSensitivity = prefs.getInt("swipe_sensitivity", 2)
        snakeView.swipeThresholdDp = sensitivityToThresholdDp(swipeSensitivity)
    }

    private fun saveSettings() {
        prefs.edit()
            .putBoolean("control_dpad", isDpadMode)
            .putInt("swipe_sensitivity", swipeSensitivity)
            .apply()
    }

    // ── Control bar ────────────────────────────────────────────────────────────

    private fun setDpadMode(dpad: Boolean) {
        isDpadMode = dpad
        applyControlBarState()
        saveSettings()
    }

    private fun applyControlBarState() {
        val activeColor   = getColor(R.color.accent_blue)
        val inactiveColor = getColor(R.color.muted)
        btnSwipeMode.setTextColor(if (!isDpadMode) activeColor else inactiveColor)
        btnDpadMode.setTextColor(if (isDpadMode) activeColor else inactiveColor)
        swipeZoneContainer.visibility = if (!isDpadMode) View.VISIBLE else View.GONE
        dpadZone.visibility           = if (isDpadMode)  View.VISIBLE else View.GONE
    }

    // ── Swipe sensitivity ──────────────────────────────────────────────────────

    private fun sensitivityToThresholdDp(s: Int): Float = when (s) {
        1 -> 40f
        3 -> 8f
        else -> 20f
    }

    // ── Settings dialog ────────────────────────────────────────────────────────

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_snake_settings, null)

        // ── Sensitivity toggle ──
        val btnSensLazy  = view.findViewById<TextView>(R.id.btnSensLazy)
        val btnSensMed   = view.findViewById<TextView>(R.id.btnSensMed)
        val btnSensZippy = view.findViewById<TextView>(R.id.btnSensZippy)

        fun refreshSens() {
            listOf(1 to btnSensLazy, 2 to btnSensMed, 3 to btnSensZippy).forEach { (s, btn) ->
                val active = s == swipeSensitivity
                val activeColor = getColor(R.color.accent_blue)
                val mutedColor  = getColor(R.color.muted)
                btn.setTextColor(if (active) activeColor else mutedColor)
                val d = resources.displayMetrics.density
                val gd = GradientDrawable()
                gd.cornerRadius = 6f * d
                if (active) {
                    gd.setColor(activeColor.withAlpha(40))
                    gd.setStroke((1.5f * d).toInt(), activeColor)
                } else {
                    gd.setColor(Color.TRANSPARENT)
                }
                btn.background = gd
            }
        }

        btnSensLazy.setOnClickListener {
            swipeSensitivity = 1
            snakeView.swipeThresholdDp = sensitivityToThresholdDp(1)
            refreshSens()
        }
        btnSensMed.setOnClickListener {
            swipeSensitivity = 2
            snakeView.swipeThresholdDp = sensitivityToThresholdDp(2)
            refreshSens()
        }
        btnSensZippy.setOnClickListener {
            swipeSensitivity = 3
            snakeView.swipeThresholdDp = sensitivityToThresholdDp(3)
            refreshSens()
        }
        refreshSens()

        // ── Theme swatches ──
        val swatchIds = listOf(R.id.swatch0, R.id.swatch1, R.id.swatch2, R.id.swatch3, R.id.swatch4, R.id.swatch5)
        val tvThemeName = view.findViewById<TextView>(R.id.tvThemeName)
        // Always show the currently effective theme (game-specific if set, else global fallback).
        var localThemeIndex = ThemeManager.effectiveThemeIndex(this, PrefsManager.GAME_SNAKE)

        fun refreshSwatches() {
            tvThemeName.text = Themes.ALL[localThemeIndex].first.name
            val d = resources.displayMetrics.density
            val strokePx = (3 * d).toInt()
            swatchIds.forEachIndexed { i, id ->
                val sw = view.findViewById<View>(id)
                val theme = Themes.ALL[i].first
                val selected = i == localThemeIndex
                sw.background = splitOvalDrawable(
                    theme.swatch, theme.rival,
                    if (selected) Color.WHITE else 0, if (selected) strokePx else 0
                )
            }
        }

        swatchIds.forEachIndexed { i, id ->
            view.findViewById<View>(id).setOnClickListener {
                localThemeIndex = i
                // Always save as a game-specific override.
                PrefsManager.setGameThemeIndex(this, PrefsManager.GAME_SNAKE, i)
                PrefsManager.setGameUsingGlobalTheme(this, PrefsManager.GAME_SNAKE, false)
                refreshSwatches()
                applyThemeToUi()
            }
        }

        refreshSwatches()

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("DONE") { _, _ -> saveSettings() }
            .setNegativeButton("RESET") { _, _ ->
                swipeSensitivity = 2
                snakeView.swipeThresholdDp = sensitivityToThresholdDp(2)
                saveSettings()
            }
            .create()

        dialog.window?.setBackgroundDrawableResource(R.color.surface)
        dialog.show()
    }

    // ── Control hint ───────────────────────────────────────────────────────────

    /**
     * After 3 consecutive low scores (<15): show control-switch hint once.
     * After 6 more consecutive low scores without switching: show it a second time.
     * Never shown more than 2 times total.
     */
    private fun checkControlHint() {
        val hintCount = PrefsManager.getSnakeControlHintCount(this)
        if (hintCount >= 2) return
        val threshold = if (hintCount == 0) 3 else 6
        if (consecutiveLowScores < threshold) return

        consecutiveLowScores = 0
        PrefsManager.setSnakeControlHintCount(this, hintCount + 1)

        // Small extra delay so the hint doesn't overlap with the leaderboard dialog.
        idleHandler.postDelayed({
            val msg = if (isDpadMode)
                "Having trouble? Try SWIPE mode for a more fluid feel!"
            else
                "Having trouble? Try D-PAD for more precise control!"
            showControlHintDialog(msg)
        }, 2_500L)
    }

    private fun showControlHintDialog(message: String) {
        val switchText = if (isDpadMode) "SWITCH TO SWIPE" else "SWITCH TO D-PAD"
        val dp = resources.displayMetrics.density

        // Root container — styled like HintManager bubble (dark navy, blue border)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (14 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * dp
                setColor(0xEE1A2040.toInt())
                setStroke((1.5f * dp).toInt(), 0xFF4488FF.toInt())
            }
        }

        // Hint message
        root.addView(TextView(this).apply {
            text = "💡 $message"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (16 * dp).toInt())
        })

        // Button row
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // DISMISS button
        val btnDismiss = TextView(this).apply {
            text = "DISMISS"
            setTextColor(0xFF6688AA.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1f)
        }

        // SWITCH button
        val btnSwitch = TextView(this).apply {
            text = switchText
            setTextColor(0xFF4488FF.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1f).apply {
                marginStart = (8 * dp).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f * dp
                setColor(0x334488FF.toInt())
                setStroke((1 * dp).toInt(), 0xFF4488FF.toInt())
            }
        }

        btnRow.addView(btnDismiss)
        btnRow.addView(btnSwitch)
        root.addView(btnRow)

        val dialog = Dialog(this)
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        btnDismiss.setOnClickListener { dialog.dismiss() }
        btnSwitch.setOnClickListener  { dialog.dismiss(); setDpadMode(!isDpadMode) }
        dialog.show()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun updateHighScore() {
        val hi = PrefsManager.getHighScore(this, PrefsManager.GAME_SNAKE)
        tvHighScore.text = "High Score: $hi"
    }

    private fun scheduleIdle() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, idleTimeout)
    }
}

private fun SeekBar.onProgress(block: (Int) -> Unit) {
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) = block(progress)
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    })
}
