package com.pocketarcade

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.pocketarcade.storage.PrefsManager
import java.io.File
import java.lang.ref.WeakReference
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
    private const val INTERVAL_MS  = 24 * 60 * 60 * 1000L
    private const val APK_FILENAME = "PocketArcade_update.apk"

    /** Auto check: silent, throttled to once per 24 h. */
    fun check(activity: Activity) {
        val elapsed = System.currentTimeMillis() - PrefsManager.getLastUpdateCheck(activity)
        if (elapsed < INTERVAL_MS) return
        fetch(activity, manual = false)
    }

    /** Manual check: always runs, shows feedback toast immediately. */
    fun checkNow(activity: Activity) {
        Toast.makeText(activity, "Checking for updates…", Toast.LENGTH_SHORT).show()
        fetch(activity, manual = true)
    }

    private fun fetch(activity: Activity, manual: Boolean) {
        Thread {
            try {
                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout    = 8_000
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
                    toast(activity, "Update check failed. Check your connection.")
                }
            }
        }.start()
    }

    private fun showUpdateDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Update Available")
            .setMessage("A new version of Pocket Arcade is available.\n\nDownload and install now?")
            .setPositiveButton("Download") { _, _ -> startDownload(activity) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startDownload(activity: Activity) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("Allow Installation")
                .setMessage("To install updates, allow Pocket Arcade to install apps in Settings first.")
                .setPositiveButton("Open Settings") { _, _ ->
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")
                        )
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val apkFile = activity.getExternalFilesDir(null)
            ?.let { File(it, APK_FILENAME) }
            ?: return toast(activity, "Storage not available. Try again later.")

        if (apkFile.exists()) apkFile.delete()

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val dlId = dm.enqueue(
            DownloadManager.Request(Uri.parse(DOWNLOAD_URL))
                .setTitle("Pocket Arcade Update")
                .setDescription("Downloading…")
                .setDestinationInExternalFilesDir(activity, null, APK_FILENAME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
        )

        toast(activity, "Downloading update…")

        val weakRef = WeakReference(activity)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != dlId) return
                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}

                val cursor = dm.query(DownloadManager.Query().setFilterById(dlId))
                val ok = cursor.use {
                    it.moveToFirst() && it.getInt(
                        it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    ) == DownloadManager.STATUS_SUCCESSFUL
                }

                weakRef.get()?.runOnUiThread {
                    val act = weakRef.get() ?: return@runOnUiThread
                    if (ok) installApk(act, apkFile)
                    else toast(act, "Download failed. Try again.")
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(activity: Activity, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun toast(activity: Activity, msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
}
