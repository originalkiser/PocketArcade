package com.pocketarcade

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.billing.BillingManager
import com.pocketarcade.storage.PrefsManager

class AdFreeActivity : AppCompatActivity() {

    private lateinit var billing: BillingManager
    private lateinit var btnPurchase: TextView
    private lateinit var btnRestore: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad_free)

        btnPurchase = findViewById(R.id.btnPurchase)
        btnRestore  = findViewById(R.id.btnRestorePurchase)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        // If already ad-free, grey out the purchase controls.
        if (PrefsManager.isAdFree(this)) {
            lockPurchaseUi()
        }

        // ── Billing ────────────────────────────────────────────────────────────
        billing = BillingManager(
            activity = this,
            onPurchased = {
                Toast.makeText(this, "Ads removed! Enjoy! ★", Toast.LENGTH_LONG).show()
                lockPurchaseUi()
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        )
        billing.connect()

        btnPurchase.setOnClickListener {
            if (!PrefsManager.isAdFree(this)) billing.launchPurchaseFlow()
        }
        btnRestore.setOnClickListener {
            billing.restorePurchases()
        }
    }

    /** Grey out the purchase controls once the user owns ad-free. */
    private fun lockPurchaseUi() {
        btnPurchase.isEnabled = false
        btnPurchase.alpha = 0.35f
        btnRestore.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyWindowBackground(this)
        val bg = ThemeManager.currentBgColor(this)
        findViewById<LinearLayout>(R.id.rootLayout).setBackgroundColor(bg)
    }

    override fun onDestroy() {
        super.onDestroy()
        billing.disconnect()
    }
}
