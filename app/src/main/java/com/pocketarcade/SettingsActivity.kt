package com.pocketarcade

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pocketarcade.ads.AdManager
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.leaderboard.showGlobalLeaderboardPicker
import com.pocketarcade.leaderboard.showUsernameSetupDialog
import com.pocketarcade.storage.PrefsManager
import com.pocketarcade.UpdateChecker

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        // ── Game Board Theme picker ───────────────────────────────────────────
        val tvThemeName = findViewById<TextView>(R.id.tvThemeName)
        val swatchIds = listOf(R.id.swatch0, R.id.swatch1, R.id.swatch2, R.id.swatch3, R.id.swatch4, R.id.swatch5, R.id.swatch6)
        var currentIndex = ThemeManager.themeIndex(this)

        fun refreshSwatches() {
            val d = resources.displayMetrics.density
            val strokePx = (3 * d).toInt()
            tvThemeName.text = if (currentIndex == Themes.SYSTEM_COLORS_INDEX) "System Colors"
                               else Themes.ALL[currentIndex].first.name
            swatchIds.forEachIndexed { i, id ->
                val selected = i == currentIndex
                val stroke   = if (selected) Color.WHITE else 0
                val bg = if (i == Themes.SYSTEM_COLORS_INDEX) {
                    // Rainbow gradient swatch for System Colors
                    rainbowOvalDrawable(strokePx.takeIf { selected } ?: 0)
                } else {
                    val theme = Themes.ALL[i].first
                    splitOvalDrawable(theme.swatch, theme.rival, stroke, if (selected) strokePx else 0)
                }
                findViewById<View>(id).background = bg
            }
        }

        swatchIds.forEachIndexed { i, id ->
            findViewById<View>(id).setOnClickListener {
                if (i == Themes.SYSTEM_COLORS_INDEX && !SystemColorTheme.isAvailable) {
                    android.widget.Toast.makeText(
                        this,
                        "System Colors requires Android 12 or later.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                currentIndex = i
                ThemeManager.setThemeIndex(this, i)
                // Blast all games to the new theme, clearing any per-game overrides.
                listOf(PrefsManager.GAME_SNAKE, PrefsManager.GAME_PONG,
                       PrefsManager.GAME_ASTEROIDS, PrefsManager.GAME_BRICKBREAKER)
                    .forEach { game -> PrefsManager.setGameUsingGlobalTheme(this, game, true) }
                refreshSwatches()
            }
        }

        refreshSwatches()

        // ── App Background Theme picker ───────────────────────────────────────
        val tvBgThemeName = findViewById<TextView>(R.id.tvBgThemeName)
        val bgSwatchIds = listOf(R.id.bgSwatch0, R.id.bgSwatch1, R.id.bgSwatch2,
                                  R.id.bgSwatch3, R.id.bgSwatch4, R.id.bgSwatch5)
        var bgIndex = ThemeManager.bgThemeIndex(this)

        fun refreshBgSwatches() {
            tvBgThemeName.text = AppBgThemes.ALL[bgIndex].name
            val d = resources.displayMetrics.density
            bgSwatchIds.forEachIndexed { i, id ->
                val theme = AppBgThemes.ALL[i]
                val selected = i == bgIndex
                val gd = android.graphics.drawable.GradientDrawable()
                gd.shape = android.graphics.drawable.GradientDrawable.OVAL
                gd.setColor(theme.swatch)
                if (selected) gd.setStroke((3 * d).toInt(), Color.WHITE)
                findViewById<View>(id).background = gd
            }
        }

        bgSwatchIds.forEachIndexed { i, id ->
            findViewById<View>(id).setOnClickListener {
                bgIndex = i
                ThemeManager.setBgThemeIndex(this, i)
                refreshBgSwatches()
                // Refresh the activity's own background immediately.
                val newBg = ThemeManager.currentBgColor(this)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(newBg))
                window.decorView.setBackgroundColor(newBg)
                findViewById<LinearLayout>(R.id.rootLayout).setBackgroundColor(newBg)
            }
        }

        refreshBgSwatches()

        // ── Other toggles ──────────────────────────────────────────────────────
        val switchDemo  = findViewById<Switch>(R.id.switchDemoMode)
        val switchSound = findViewById<Switch>(R.id.switchSound)

        // Leaderboard size
        val tvLbSize  = findViewById<TextView>(R.id.tvLeaderboardSize)
        val btnLbMinus = findViewById<TextView>(R.id.btnLbMinus)
        val btnLbPlus  = findViewById<TextView>(R.id.btnLbPlus)
        fun refreshLbSize() { tvLbSize.text = PrefsManager.getLeaderboardSize(this).toString() }
        refreshLbSize()
        btnLbMinus.setOnClickListener {
            val n = PrefsManager.getLeaderboardSize(this)
            if (n > 1) PrefsManager.setLeaderboardSize(this, n - 1)
            refreshLbSize()
        }
        btnLbPlus.setOnClickListener {
            val n = PrefsManager.getLeaderboardSize(this)
            if (n < 15) PrefsManager.setLeaderboardSize(this, n + 1)
            refreshLbSize()
        }

        // Global leaderboard row
        val rowGlobal   = findViewById<LinearLayout>(R.id.rowGlobalLeaderboard)
        val tvGlobalDesc = findViewById<TextView>(R.id.tvGlobalLeaderboardDesc)
        fun refreshGlobalRow() {
            val username = PrefsManager.getGlobalUsername(this)
            tvGlobalDesc.text = if (username != null) "@$username" else "Tap to register"
        }
        refreshGlobalRow()
        rowGlobal.setOnClickListener {
            if (PrefsManager.getGlobalUsername(this) != null) {
                showGlobalLeaderboardPicker(this)
            } else {
                GlobalLeaderboard.ensureSignedIn(
                    onReady = { uid ->
                        runOnUiThread {
                            showUsernameSetupDialog(this, uid, pendingScore = null, onSuccess = {
                                refreshGlobalRow()
                            })
                        }
                    },
                    onError = { msg ->
                        runOnUiThread {
                            Toast.makeText(this, "Sign-in failed: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }

        val rowAdFree       = findViewById<LinearLayout>(R.id.rowAdFree)
        val tvAdFreeTitle   = findViewById<TextView>(R.id.tvAdFreeTitle)
        val tvAdFreeDesc    = findViewById<TextView>(R.id.tvAdFreeDesc)
        val tvAdFreeChevron = findViewById<TextView>(R.id.tvAdFreeChevron)
        val btnRestore      = findViewById<TextView>(R.id.btnRestore)
        val rowJokeBanner   = findViewById<LinearLayout>(R.id.rowJokeBanner)
        val switchJokeAds   = findViewById<Switch>(R.id.switchJokeAds)
        // ── Display Mode picker ────────────────────────────────────────────────
        val btnModeDark   = findViewById<TextView>(R.id.btnModeDark)
        val btnModeLight  = findViewById<TextView>(R.id.btnModeLight)
        val btnModeSystem = findViewById<TextView>(R.id.btnModeSystem)

        fun refreshModeButtons() {
            val active   = getColor(R.color.accent_yellow)
            val inactive = getColor(R.color.muted)
            val current  = ThemeManager.getDisplayMode(this)
            btnModeDark  .setTextColor(if (current == PrefsManager.DisplayMode.DARK)   active else inactive)
            btnModeLight .setTextColor(if (current == PrefsManager.DisplayMode.LIGHT)  active else inactive)
            btnModeSystem.setTextColor(if (current == PrefsManager.DisplayMode.SYSTEM) active else inactive)
        }
        refreshModeButtons()

        btnModeDark  .setOnClickListener { ThemeManager.setDisplayMode(this, PrefsManager.DisplayMode.DARK);   refreshModeButtons() }
        btnModeLight .setOnClickListener { ThemeManager.setDisplayMode(this, PrefsManager.DisplayMode.LIGHT);  refreshModeButtons() }
        btnModeSystem.setOnClickListener { ThemeManager.setDisplayMode(this, PrefsManager.DisplayMode.SYSTEM); refreshModeButtons() }

        val btnCheckUpdate  = findViewById<TextView>(R.id.btnCheckUpdate)
        val btnShareApp     = findViewById<TextView>(R.id.btnShareApp)
        val btnWhatsNew     = findViewById<TextView>(R.id.btnWhatsNew)
        val btnCredits      = findViewById<TextView>(R.id.btnCredits)
        val btnReset        = findViewById<TextView>(R.id.btnResetScores)
        val tvVersion       = findViewById<TextView>(R.id.tvVersion)
        tvVersion.text      = "v${BuildConfig.VERSION_NAME}"

        // ── Secret debug screen: tap version 7 times within 2 s per tap ──────
        var versionTaps  = 0
        var lastVersionTap = 0L
        tvVersion.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastVersionTap > 2_000) versionTaps = 0
            lastVersionTap = now
            versionTaps++
            when {
                versionTaps == 4 -> Toast.makeText(this,
                    "3 more taps to enter debug mode.", Toast.LENGTH_SHORT).show()
                versionTaps >= 7 -> {
                    versionTaps = 0
                    startActivity(Intent(this, DebugLogActivity::class.java))
                }
            }
        }

        switchDemo.isChecked  = PrefsManager.isDemoModeEnabled(this)
        switchSound.isChecked = PrefsManager.isSoundEnabled(this)

        // ── Friend notification switches ───────────────────────────────────────
        val switchNotifNew    = findViewById<Switch>(R.id.switchNotifNewScore)
        val switchNotifBeaten = findViewById<Switch>(R.id.switchNotifBeaten)
        switchNotifNew.isChecked    = PrefsManager.isNotifNewScore(this)
        switchNotifBeaten.isChecked = PrefsManager.isNotifBeaten(this)

        fun requestNotifPermIfNeeded(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    FriendNudgeManager.REQ_NOTIF_PERM)
                false   // wait for result — revert if denied
            }
        }

        switchNotifNew.setOnCheckedChangeListener { _, checked ->
            if (PrefsManager.getGlobalUsername(this) == null) {
                switchNotifNew.isChecked = false
                Toast.makeText(this,
                    "Register on the global leaderboard to enable notifications.",
                    Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            if (checked && !requestNotifPermIfNeeded()) {
                // Permission prompt shown — defer saving until onRequestPermissionsResult
                return@setOnCheckedChangeListener
            }
            PrefsManager.setNotifNewScore(this, checked)
        }

        switchNotifBeaten.setOnCheckedChangeListener { _, checked ->
            if (PrefsManager.getGlobalUsername(this) == null) {
                switchNotifBeaten.isChecked = false
                Toast.makeText(this,
                    "Register on the global leaderboard to enable notifications.",
                    Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            if (checked && !requestNotifPermIfNeeded()) {
                return@setOnCheckedChangeListener
            }
            PrefsManager.setNotifBeaten(this, checked)
        }

        refreshAdFreeUi(tvAdFreeTitle, tvAdFreeDesc, tvAdFreeChevron, rowAdFree,
            rowJokeBanner, switchJokeAds)

        switchDemo.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setDemoModeEnabled(this, checked)
        }
        switchSound.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setSoundEnabled(this, checked)
        }
        switchJokeAds.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setJokeAdsEnabled(this, checked)
            AdManager.populateBannerContainer(findViewById(R.id.adContainer))
        }

        // Both the ad-free row and restore button navigate to the landing page.
        val openAdFreePage = View.OnClickListener {
            startActivity(Intent(this, AdFreeActivity::class.java))
        }
        rowAdFree.setOnClickListener(openAdFreePage)
        btnRestore.setOnClickListener(openAdFreePage)
        btnCheckUpdate.setOnClickListener { UpdateChecker.checkNow(this) }
        btnShareApp.setOnClickListener { ShareUtils.shareApp(this) }
        btnWhatsNew.setOnClickListener { showChangelogDialog(this) }
        btnCredits.setOnClickListener { showCreditsDialog(this) }
        btnReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.reset_scores_confirm))
                .setPositiveButton(R.string.yes) { _, _ ->
                    PrefsManager.resetHighScores(this)
                    Toast.makeText(this, "Scores reset.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun refreshAdFreeUi(
        title: TextView, desc: TextView, chevron: TextView, row: LinearLayout,
        jokeRow: LinearLayout? = null, jokeSwitch: Switch? = null
    ) {
        if (PrefsManager.isAdFree(this)) {
            title.text = getString(R.string.ad_free_purchased)
            desc.text = "All ads removed. Thank you! ★"
            chevron.visibility = View.GONE
            row.isClickable = false
            // Show joke banner toggle — only relevant once ad-free is active.
            jokeRow?.visibility = View.VISIBLE
            jokeSwitch?.isChecked = PrefsManager.isJokeAdsEnabled(this)
        } else {
            title.text = getString(R.string.ad_free_title)
            desc.text = getString(R.string.ad_free_desc)
            chevron.visibility = View.VISIBLE
            row.isClickable = true
            jokeRow?.visibility = View.GONE
        }
    }

    /**
     * Handles the POST_NOTIFICATIONS permission result triggered when the user
     * flips a notification switch for the first time on API 33+.
     * If denied: revert both switches and clear the prefs.
     * If granted: save both switch states as they currently stand.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != FriendNudgeManager.REQ_NOTIF_PERM) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        val switchNotifNew    = findViewById<Switch>(R.id.switchNotifNewScore)
        val switchNotifBeaten = findViewById<Switch>(R.id.switchNotifBeaten)
        if (granted) {
            // Permission granted — commit whatever is currently shown
            PrefsManager.setNotifNewScore(this, switchNotifNew.isChecked)
            PrefsManager.setNotifBeaten(this, switchNotifBeaten.isChecked)
        } else {
            // Denied — revert both switches
            switchNotifNew.isChecked    = false
            switchNotifBeaten.isChecked = false
            PrefsManager.setNotifNewScore(this, false)
            PrefsManager.setNotifBeaten(this, false)
            Toast.makeText(
                this,
                "Notification permission denied. You can enable it in Android Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        UpdateChecker.checkResumeDownload(this)
        ThemeManager.applyWindowBackground(this)
        val bg = ThemeManager.currentBgColor(this)
        findViewById<LinearLayout>(R.id.rootLayout).setBackgroundColor(bg)

        // Refresh ad-free state in case user purchased/restored on the AdFreeActivity screen.
        val rowAdFree       = findViewById<LinearLayout>(R.id.rowAdFree)     ?: return
        val tvAdFreeTitle   = findViewById<TextView>(R.id.tvAdFreeTitle)     ?: return
        val tvAdFreeDesc    = findViewById<TextView>(R.id.tvAdFreeDesc)      ?: return
        val tvAdFreeChevron = findViewById<TextView>(R.id.tvAdFreeChevron)   ?: return
        val rowJokeBanner   = findViewById<LinearLayout>(R.id.rowJokeBanner)
        val switchJokeAds   = findViewById<Switch>(R.id.switchJokeAds)
        refreshAdFreeUi(tvAdFreeTitle, tvAdFreeDesc, tvAdFreeChevron, rowAdFree,
            rowJokeBanner, switchJokeAds)
        AdManager.populateBannerContainer(findViewById(R.id.adContainer))
    }

}
