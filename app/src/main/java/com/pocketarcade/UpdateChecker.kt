package com.pocketarcade

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.pocketarcade.storage.PrefsManager
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object UpdateChecker {

    private const val API_URL =
        "https://api.github.com/repos/originalkiser/PocketArcade/releases/latest"
    private const val DOWNLOAD_URL =
        "https://github.com/originalkiser/PocketArcade/releases/latest/download/PocketArcade.apk"
    private const val INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** Auto check: silent, throttled to once per 24 h. */
    fun check(activity: Activity) {
        val elapsed = System.currentTimeMillis() - PrefsManager.getLastUpdateCheck(activity)
        if (elapsed < INTERVAL_MS) return
        fetch(activity, manual = false)
    }

    /** Manual check: always runs, shows toast when already up to date. */
    fun checkNow(activity: Activity) = fetch(activity, manual = true)

    private fun fetch(activity: Activity, manual: Boolean) {
        Thread {
            try {
                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                PrefsManager.setLastUpdateCheck(activity, System.currentTimeMillis())

                val publishedAt = Regex(""""published_at"\s*:\s*"([^"]+)"""")
                    .find(json)?.groupValues?.get(1)
                    ?: run {
                        if (manual) activity.runOnUiThread {
                            toast(activity, "Could not read release info.")
                        }
                        return@Thread
                    }

                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                val releaseTime = fmt.parse(publishedAt)?.time ?: return@Thread

                activity.runOnUiThread {
                    if (releaseTime > BuildConfig.BUILD_TIME) {
                        showUpdateDialog(activity)
                    } else if (manual) {
                        toast(activity, "You're up to date!")
                    }
                }
            } catch (_: Exception) {
                if (manual) activity.runOnUiThread {
                    toast(activity, "Update check failed. Try again later.")
                }
            }
        }.start()
    }

    private fun showUpdateDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Update Available")
            .setMessage("A new version of Pocket Arcade is available on GitHub.\n\nDownload the latest APK?")
            .setPositiveButton("Download") { _, _ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL)))
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun toast(activity: Activity, msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
}
