package com.pocketarcade.games.snake

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.pocketarcade.splitOvalDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
    private lateinit var dpadContainer: FrameLayout
    private lateinit var btnRestart: View
    private lateinit var btnUp: View
    private lateinit var btnDown: View
    private lateinit var btnLeft: View
    private lateinit var btnRight: View
    private lateinit var btnSwipeMode: TextView
    private lateinit var btnDpadMode: TextView
    private lateinit var btnLightMode: TextView
    private lateinit var btnSound: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnLeaderboard: TextView

    private var isDpadMode = false
    private var gameStartTime = 0L
    private var fruitsThisGame = 0
    private var consecutiveLowScores = 0  // resets when score >= 15; not persisted

    private var dpadButtonSizeDp = 56f
    private var dpadHSpreadDp = 0f
    private var dpadVSpacingDp = 4f
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
        dpadContainer      = findViewById(R.id.dpadContainer)
        btnRestart         = findViewById(R.id.btnRestart)
        btnUp              = findViewById(R.id.btnUp)
        btnDown            = findViewById(R.id.btnDown)
        btnLeft            = findViewById(R.id.btnLeft)
        btnRight           = findViewById(R.id.btnRight)
        btnSwipeMode       = findViewById(R.id.btnSwipeMode)
        btnDpadMode        = findViewById(R.id.btnDpadMode)
        btnLightMode       = findViewById(R.id.btnLightMode)
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

        btnUp.setOnTouchListener    { _, e -> if (e.action == MotionEvent.ACTION_DOWN) snakeView.dpadUp();    true }
        btnDown.setOnTouchListener  { _, e -> if (e.action == MotionEvent.ACTION_DOWN) snakeView.dpadDown();  true }
        btnLeft.setOnTouchListener  { _, e -> if (e.action == MotionEvent.ACTION_DOWN) snakeView.dpadLeft();  true }
        btnRight.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_DOWN) snakeView.dpadRight(); true }

        btnSwipeMode.setOnClickListener  { setDpadMode(false) }
        btnDpadMode.setOnClickListener   { setDpadMode(true) }
        btnLightMode.setOnClickListener  { toggleLightMode() }
        btnSound.setOnClickListener      { toggleSound() }
        btnSettings.setOnClickListener   { startActivity(android.content.Intent(this, com.pocketarcade.SettingsActivity::class.java)) }
        btnLeaderboard.setOnClickListener { showLeaderboardDialog(this, PrefsManager.GAME_SNAKE) }

        updateSoundButton()

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
        applyDpadLayout()
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

        btnLightMode.text = if (ThemeManager.isLightMode(this)) "☀" else "🌙"
    }

    private fun toggleLightMode() {
        ThemeManager.setLightMode(this, !ThemeManager.isLightMode(this))
        applyThemeToUi()
    }

    private fun toggleSound() {
        PrefsManager.setSoundEnabled(this, !PrefsManager.isSoundEnabled(this))
        updateSoundButton()
    }

    private fun updateSoundButton() {
        btnSound.text = if (PrefsManager.isSoundEnabled(this)) "🔊" else "🔇"
    }

    // ── Settings persistence ───────────────────────────────────────────────────

    private fun loadSettings() {
        isDpadMode       = prefs.getBoolean("control_dpad", false)
        dpadButtonSizeDp = prefs.getFloat("dpad_btn_size", 56f)
        dpadHSpreadDp    = prefs.getFloat("dpad_h_spread", 0f)
        dpadVSpacingDp   = prefs.getFloat("dpad_v_spacing", 0f)
        swipeSensitivity = prefs.getInt("swipe_sensitivity", 2)

        snakeView.swipeThresholdDp = sensitivityToThresholdDp(swipeSensitivity)
    }

    private fun saveSettings() {
        prefs.edit()
            .putBoolean("control_dpad", isDpadMode)
            .putFloat("dpad_btn_size", dpadButtonSizeDp)
            .putFloat("dpad_h_spread", dpadHSpreadDp)
            .putFloat("dpad_v_spacing", dpadVSpacingDp)
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
        dpadContainer.visibility      = if (isDpadMode)  View.VISIBLE else View.GONE
    }

    // ── D-pad layout ───────────────────────────────────────────────────────────

    private fun applyDpadLayout() {
        val B = dpadButtonSizeDp
        val S = dpadHSpreadDp
        val V = dpadVSpacingDp
        val d = resources.displayMetrics.density

        val bPx = (B * d).toInt()
        val sPx = (S * d).toInt()
        val vPx = (V * d).toInt()

        val contW   = 3 * bPx + sPx
        val contH   = 3 * bPx + 2 * vPx
        val centerX = bPx + sPx / 2

        val dcLp = dpadContainer.layoutParams as FrameLayout.LayoutParams
        dcLp.width   = contW
        dcLp.height  = contH
        dcLp.gravity = Gravity.CENTER
        dpadContainer.layoutParams = dcLp

        setDpadBtn(btnUp,    centerX,        0,               bPx)
        setDpadBtn(btnLeft,  0,              bPx + vPx,       bPx)
        setDpadBtn(btnRight, 2 * bPx + sPx, bPx + vPx,       bPx)
        setDpadBtn(btnDown,  centerX,        2 * (bPx + vPx), bPx)
    }

    private fun setDpadBtn(v: View, x: Int, y: Int, sz: Int) {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        lp.gravity    = Gravity.TOP or Gravity.START
        lp.leftMargin = x
        lp.topMargin  = y
        lp.width      = sz
        lp.height     = sz
        v.layoutParams = lp
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

        // ── Seekbars ──
        val seekBtnSize  = view.findViewById<SeekBar>(R.id.seekBtnSize)
        val tvBtnSize    = view.findViewById<TextView>(R.id.tvBtnSize)
        val seekHSpread  = view.findViewById<SeekBar>(R.id.seekHSpread)
        val tvHSpread    = view.findViewById<TextView>(R.id.tvHSpread)
        val seekVSpacing = view.findViewById<SeekBar>(R.id.seekVSpacing)
        val tvVSpacing   = view.findViewById<TextView>(R.id.tvVSpacing)

        seekBtnSize.progress  = dpadButtonSizeDp.toInt()
        tvBtnSize.text        = "${dpadButtonSizeDp.toInt()}dp"
        seekHSpread.progress  = dpadHSpreadDp.toInt()
        tvHSpread.text        = "${dpadHSpreadDp.toInt()}dp"
        seekVSpacing.progress = dpadVSpacingDp.toInt()
        tvVSpacing.text       = "${dpadVSpacingDp.toInt()}dp"

        seekBtnSize.onProgress { p ->
            dpadButtonSizeDp = p.toFloat()
            tvBtnSize.text = "${p}dp"
            applyDpadLayout()
        }
        seekHSpread.onProgress { p ->
            dpadHSpreadDp = p.toFloat()
            tvHSpread.text = "${p}dp"
            applyDpadLayout()
        }
        seekVSpacing.onProgress { p ->
            dpadVSpacingDp = p.toFloat()
            tvVSpacing.text = "${p}dp"
            applyDpadLayout()
        }

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
                dpadButtonSizeDp = 56f; dpadHSpreadDp = 0f
                dpadVSpacingDp = 0f; swipeSensitivity = 2
                snakeView.swipeThresholdDp = sensitivityToThresholdDp(2)
                applyDpadLayout()
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
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(switchText) { _, _ -> setDpadMode(!isDpadMode) }
            .setNegativeButton("DISMISS", null)
            .create()
            .also { it.window?.setBackgroundDrawableResource(R.color.surface) }
            .show()
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
