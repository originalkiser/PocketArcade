package com.pocketarcade

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.billing.BillingManager
import com.pocketarcade.storage.PrefsManager

class AdFreeActivity : AppCompatActivity() {

    private lateinit var billing: BillingManager

    companion object {
        /**
         * Promo codes validated client-side.
         * Each code unlocks ad-free on the device when entered.
         */
        private val VALID_PROMO_CODES = setOf(
            "ARCADE22",
            "NOADS",
            "POCKETFREE",
            "GIFTED",
            "DEVCODE"
        )
    }

    private lateinit var btnPurchase: TextView
    private lateinit var btnApply: TextView
    private lateinit var btnRestore: TextView
    private lateinit var etPromo: EditText
    private lateinit var tvPromoStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad_free)

        btnPurchase  = findViewById(R.id.btnPurchase)
        btnApply     = findViewById(R.id.btnApplyPromo)
        btnRestore   = findViewById(R.id.btnRestorePurchase)
        etPromo      = findViewById(R.id.etPromoCode)
        tvPromoStatus = findViewById(R.id.tvPromoStatus)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        // If already ad-free (e.g., opened from settings as "Purchased"), show completed state.
        if (PrefsManager.isAdFree(this)) {
            lockPurchaseUi(alreadyOwned = true)
        }

        // ── Promo code ─────────────────────────────────────────────────────────
        btnApply.setOnClickListener {
            val code = etPromo.text.toString().trim().uppercase()
            hideKeyboard(etPromo)
            when {
                code.isEmpty() ->
                    showPromoStatus("Enter a promo code first.", error = true)
                code in VALID_PROMO_CODES -> {
                    PrefsManager.setAdFree(this, true)
                    showPromoStatus("✓  Code applied — enjoy ad-free!", error = false)
                    lockPurchaseUi(alreadyOwned = false)
                }
                else ->
                    showPromoStatus("Invalid code. Check and try again.", error = true)
            }
        }

        // ── Billing ────────────────────────────────────────────────────────────
        billing = BillingManager(
            activity = this,
            onPurchased = {
                Toast.makeText(this, "Ads removed! Enjoy! ★", Toast.LENGTH_LONG).show()
                lockPurchaseUi(alreadyOwned = false)
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
    private fun lockPurchaseUi(alreadyOwned: Boolean) {
        btnPurchase.isEnabled = false
        btnPurchase.alpha = 0.35f
        btnApply.isEnabled = false
        etPromo.isEnabled = false
        btnRestore.isEnabled = false
        if (alreadyOwned && tvPromoStatus.visibility == View.GONE) {
            showPromoStatus("✓  Ad-free already active on this device.", error = false)
        }
    }

    private fun showPromoStatus(msg: String, error: Boolean) {
        tvPromoStatus.text = msg
        tvPromoStatus.setTextColor(
            if (error) getColor(R.color.accent_red) else getColor(R.color.accent_green)
        )
        tvPromoStatus.visibility = View.VISIBLE
    }

    private fun hideKeyboard(view: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
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
