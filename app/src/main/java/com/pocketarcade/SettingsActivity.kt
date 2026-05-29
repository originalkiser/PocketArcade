package com.pocketarcade

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.ads.AdManager
import com.pocketarcade.billing.BillingManager
import com.pocketarcade.storage.PrefsManager
import com.pocketarcade.UpdateChecker

class SettingsActivity : AppCompatActivity() {

    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        AdManager.populateBannerContainer(findViewById(R.id.adContainer))

        // ── Theme picker ──────────────────────────────────────────────────────
        val tvThemeName = findViewById<TextView>(R.id.tvThemeName)
        val swatchIds = listOf(R.id.swatch0, R.id.swatch1, R.id.swatch2, R.id.swatch3, R.id.swatch4, R.id.swatch5)
        var currentIndex = ThemeManager.themeIndex(this)

        fun refreshSwatches() {
            tvThemeName.text = Themes.ALL[currentIndex].first.name
            val d = resources.displayMetrics.density
            val strokePx = (3 * d).toInt()
            swatchIds.forEachIndexed { i, id ->
                val theme = Themes.ALL[i].first
                val selected = i == currentIndex
                findViewById<View>(id).background = splitOvalDrawable(
                    theme.swatch, theme.rival,
                    if (selected) Color.WHITE else 0, if (selected) strokePx else 0
                )
            }
        }

        swatchIds.forEachIndexed { i, id ->
            findViewById<View>(id).setOnClickListener {
                currentIndex = i
                ThemeManager.setThemeIndex(this, i)
                refreshSwatches()
            }
        }

        refreshSwatches()

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

        val rowAdFree   = findViewById<LinearLayout>(R.id.rowAdFree)
        val tvAdFreeTitle   = findViewById<TextView>(R.id.tvAdFreeTitle)
        val tvAdFreeDesc    = findViewById<TextView>(R.id.tvAdFreeDesc)
        val tvAdFreeChevron = findViewById<TextView>(R.id.tvAdFreeChevron)
        val btnRestore     = findViewById<TextView>(R.id.btnRestore)
        val btnCheckUpdate = findViewById<TextView>(R.id.btnCheckUpdate)
        val btnShareApp    = findViewById<TextView>(R.id.btnShareApp)
        val btnWhatsNew    = findViewById<TextView>(R.id.btnWhatsNew)
        val btnCredits     = findViewById<TextView>(R.id.btnCredits)
        val btnReset       = findViewById<TextView>(R.id.btnResetScores)
        findViewById<TextView>(R.id.tvVersion).text = "v${BuildConfig.VERSION_NAME}"

        switchDemo.isChecked  = PrefsManager.isDemoModeEnabled(this)
        switchSound.isChecked = PrefsManager.isSoundEnabled(this)

        refreshAdFreeUi(tvAdFreeTitle, tvAdFreeDesc, tvAdFreeChevron, rowAdFree)

        switchDemo.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setDemoModeEnabled(this, checked)
        }
        switchSound.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setSoundEnabled(this, checked)
        }

        billing = BillingManager(
            activity = this,
            onPurchased = {
                refreshAdFreeUi(tvAdFreeTitle, tvAdFreeDesc, tvAdFreeChevron, rowAdFree)
                AdManager.populateBannerContainer(findViewById(R.id.adContainer))
                Toast.makeText(this, "Ads removed! Enjoy!", Toast.LENGTH_LONG).show()
            },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
        billing.connect()

        rowAdFree.setOnClickListener {
            if (!PrefsManager.isAdFree(this)) billing.launchPurchaseFlow()
        }
        btnRestore.setOnClickListener { billing.restorePurchases() }
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
        title: TextView, desc: TextView, chevron: TextView, row: LinearLayout
    ) {
        if (PrefsManager.isAdFree(this)) {
            title.text = getString(R.string.ad_free_purchased)
            desc.text = "All ads removed. Thank you! ★"
            chevron.visibility = View.GONE
            row.isClickable = false
        } else {
            title.text = getString(R.string.ad_free_title)
            desc.text = getString(R.string.ad_free_desc)
            chevron.visibility = View.VISIBLE
            row.isClickable = true
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyWindowBackground(this)
        val bg = ThemeManager.currentTheme(this).bg
        findViewById<LinearLayout>(R.id.rootLayout).setBackgroundColor(bg)
    }

    override fun onDestroy() {
        super.onDestroy()
        billing.disconnect()
    }

    private fun splitOvalDrawable(c1: Int, c2: Int, strokeColor: Int, strokePx: Int): Drawable {
        return object : Drawable() {
            private val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c1 }
            private val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c2 }
            private val ps = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = strokeColor
                strokeWidth = strokePx.toFloat()
            }
            override fun draw(canvas: Canvas) {
                val r = RectF(bounds)
                canvas.drawArc(r, 90f, 180f, true, p1)
                canvas.drawArc(r, 270f, 180f, true, p2)
                if (strokePx > 0) {
                    val inset = strokePx / 2f
                    canvas.drawOval(RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset), ps)
                }
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(cf: ColorFilter?) {}
            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }
}
