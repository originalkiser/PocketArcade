package com.pocketarcade.billing

import android.app.Activity
import com.android.billingclient.api.*
import com.pocketarcade.BuildConfig
import com.pocketarcade.storage.PrefsManager

class BillingManager(
    private val activity: Activity,
    private val onPurchased: () -> Unit,
    private val onError: (String) -> Unit
) {
    private lateinit var billingClient: BillingClient

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) handlePurchase(purchase)
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            onError("Billing error: ${result.debugMessage}")
        }
    }

    fun connect() {
        billingClient = BillingClient.newBuilder(activity)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryExistingPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    fun disconnect() {
        if (::billingClient.isInitialized) billingClient.endConnection()
    }

    fun launchPurchaseFlow() {
        if (!billingClient.isReady) {
            onError("Billing not ready. Please try again.")
            return
        }
        val sku = BuildConfig.BILLING_SKU_AD_FREE
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        ) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || details.isEmpty()) {
                onError("Product not found. Check Play Console setup.")
                return@queryProductDetailsAsync
            }
            val productDetails = details[0]
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )
                )
                .build()
            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            onError("Billing not ready.")
            return
        }
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { _, purchases ->
            var restored = false
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    BuildConfig.BILLING_SKU_AD_FREE in purchase.products
                ) {
                    PrefsManager.setAdFree(activity, true)
                    acknowledgePurchase(purchase)
                    restored = true
                }
            }
            if (restored) activity.runOnUiThread { onPurchased() }
            else activity.runOnUiThread { onError("No previous purchase found.") }
        }
    }

    private fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { _, purchases ->
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    BuildConfig.BILLING_SKU_AD_FREE in purchase.products
                ) {
                    PrefsManager.setAdFree(activity, true)
                    acknowledgePurchase(purchase)
                    activity.runOnUiThread { onPurchased() }
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            BuildConfig.BILLING_SKU_AD_FREE in purchase.products
        ) {
            PrefsManager.setAdFree(activity, true)
            acknowledgePurchase(purchase)
            activity.runOnUiThread { onPurchased() }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) {}
        }
    }
}
