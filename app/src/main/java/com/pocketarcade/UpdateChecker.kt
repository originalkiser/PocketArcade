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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.pocketarcade.storage.PrefsManager
import java.io.File
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val API_URL =
        "https://api.github.com/repos/originalkiser/PocketArcade/releases/latest"
    private const val FALLBACK_DOWNLOAD_URL =
        "https://github.com/originalkiser/PocketArcade/releases/latest/download/PocketArcade.apk"
    private const val INTERVAL_MS  = 24 * 60 * 60 * 1000L
    private const val APK_FILENAME = "PocketArcade_update.apk"

    /** Holds the APK URL while waiting for the user to grant install-unknown-apps permission. */
    private var pendingApkUrl: String? = null

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

    /**
     * Call from onResume() of any activity that can trigger the update flow.
     * If the user just granted install-unknown-apps permission, this resumes the download.
     */
    fun checkResumeDownload(activity: Activity) {
        val url = pendingApkUrl ?: return
        if (activity.packageManager.canRequestPackageInstalls()) {
            pendingApkUrl = null
            performDownload(activity, url)
        }
    }

    /** Returns true if [remote] version string is strictly newer than [local]. */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.trimStart('v').split(".").mapNotNull { it.toIntOrNull() }
        val l = local.trimStart('v').split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
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

                val tagName = Regex(""""tag_name"\s*:\s*"([^"]+)"""")
                    .find(json)?.groupValues?.get(1)
                    ?: run {
                        if (manual) activity.runOnUiThread {
                            toast(activity, "Could not read release info.")
                        }
                        return@Thread
                    }

                // Prefer the actual asset URL from the release; fall back to the static URL.
                val apkUrl = Regex(""""browser_download_url"\s*:\s*"([^"]+\.apk)"""")
                    .find(json)?.groupValues?.get(1)
                    ?: FALLBACK_DOWNLOAD_URL

                activity.runOnUiThread {
                    val currentTag = "v${BuildConfig.VERSION_NAME}"
                    if (isNewerVersion(tagName, currentTag)) {
                        showUpdateDialog(activity, tagName, apkUrl)
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

    private fun showUpdateDialog(activity: Activity, tagName: String, apkUrl: String) {
        showStyledDialog(
            activity,
            title    = "UPDATE AVAILABLE",
            message  = "Version $tagName of Pocket Arcade is ready.\nDownload and install now?",
            positive = "DOWNLOAD UPDATE",
            negative = "LATER"
        ) { startDownload(activity, apkUrl) }
    }

    private fun showPermissionDialog(activity: Activity) {
        showStyledDialog(
            activity,
            title    = "PERMISSION REQUIRED",
            message  = "To install updates, enable 'Install unknown apps' for Pocket Arcade in Settings.",
            positive = "OPEN SETTINGS",
            negative = "CANCEL"
        ) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        }
    }

    private fun showStyledDialog(
        activity: Activity,
        title: String,
        message: String,
        positive: String,
        negative: String,
        onPositive: () -> Unit
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_update, null)
        view.findViewById<TextView>(R.id.tvUpdateTitle).text    = title
        view.findViewById<TextView>(R.id.tvUpdateMessage).text  = message
        view.findViewById<TextView>(R.id.btnUpdatePositive).text = positive
        view.findViewById<TextView>(R.id.btnUpdateNegative).text = negative
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        view.findViewById<TextView>(R.id.btnUpdatePositive).setOnClickListener {
            dialog.dismiss()
            onPositive()
        }
        view.findViewById<TextView>(R.id.btnUpdateNegative).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun startDownload(activity: Activity, apkUrl: String) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            pendingApkUrl = apkUrl   // retry after permission is granted in onResume
            showPermissionDialog(activity)
            return
        }
        pendingApkUrl = null
        performDownload(activity, apkUrl)
    }

    private fun performDownload(activity: Activity, apkUrl: String) {
        val apkFile = activity.getExternalFilesDir(null)
            ?.let { File(it, APK_FILENAME) }
            ?: return toast(activity, "Storage not available. Try again later.")

        if (apkFile.exists()) apkFile.delete()

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val dlId = dm.enqueue(
            DownloadManager.Request(Uri.parse(apkUrl))
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
