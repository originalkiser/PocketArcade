package com.pocketarcade

import android.graphics.Color
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
            swatchIds.forEachIndexed { i, id ->
                val gd = GradientDrawable()
                gd.shape = GradientDrawable.OVAL
                gd.setColor(Themes.ALL[i].first.swatch)
                if (i == currentIndex) gd.setStroke((3 * d).toInt(), Color.WHITE)
                findViewById<View>(id).background = gd
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
        val rowAdFree   = findViewById<LinearLayout>(R.id.rowAdFree)
        val tvAdFreeTitle   = findViewById<TextView>(R.id.tvAdFreeTitle)
        val tvAdFreeDesc    = findViewById<TextView>(R.id.tvAdFreeDesc)
        val tvAdFreeChevron = findViewById<TextView>(R.id.tvAdFreeChevron)
        val btnRestore  = findViewById<TextView>(R.id.btnRestore)
        val btnCheckUpdate = findViewById<TextView>(R.id.btnCheckUpdate)
        val btnReset    = findViewById<TextView>(R.id.btnResetScores)

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

    override fun onDestroy() {
        super.onDestroy()
        billing.disconnect()
    }
}
