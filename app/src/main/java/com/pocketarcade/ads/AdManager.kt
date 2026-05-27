package com.pocketarcade.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdListener
import com.pocketarcade.BuildConfig
import com.pocketarcade.storage.PrefsManager

object AdManager {

    fun populateBannerContainer(container: FrameLayout) {
        val ctx = container.context
        container.removeAllViews()

        if (PrefsManager.isAdFree(ctx)) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE

        if (!hasInternet(ctx)) {
            container.addView(makeFakeAd(ctx))
            return
        }

        val adView = AdView(ctx).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.BANNER_AD_UNIT_ID
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    container.removeAllViews()
                    container.addView(makeFakeAd(ctx))
                }
            }
            loadAd(AdRequest.Builder().build())
        }
        container.addView(adView)
    }

    private fun makeFakeAd(ctx: Context): FakeAdView {
        return FakeAdView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun hasInternet(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
