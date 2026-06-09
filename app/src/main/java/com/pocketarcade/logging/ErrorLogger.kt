package com.pocketarcade.logging

import android.content.Context
import android.os.Build
import com.pocketarcade.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Silent local error log — JSON array stored in internal files dir.
 * Capped at MAX_EVENTS (100) entries; oldest rotated out when the cap is reached.
 * Never writes to logcat, shows no UI, and never throws.
 */
object ErrorLogger {

    private const val LOG_FILE   = "error_log.json"
    private const val MAX_EVENTS = 100

    // ── Write ─────────────────────────────────────────────────────────────────

    fun log(
        ctx: Context,
        type: String,
        message: String,
        stackTrace: String,
        screen: String,
        userId: String = ""
    ) {
        try {
            val entry = JSONObject().apply {
                put("timestamp",  isoNow())
                put("type",       type.take(120))
                put("message",    message.take(500))
                put("stack",      stackTrace.take(4000))
                put("screen",     screen.take(80))
                put("app_version", BuildConfig.VERSION_NAME)
                put("os_version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                put("device",     "${Build.MANUFACTURER} ${Build.MODEL}".take(80))
                put("user_id",    userId.take(80))
                put("pushed",     false)
            }
            val file = logFile(ctx)
            val arr  = readArray(file)
            arr.put(entry)
            rotate(arr)
            file.writeText(arr.toString())
        } catch (_: Exception) { /* must never propagate */ }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** All stored entries (oldest → newest). */
    fun getAll(ctx: Context): JSONArray =
        try { readArray(logFile(ctx)) } catch (_: Exception) { JSONArray() }

    /** Entries that have not yet been pushed to Firebase. */
    fun getPending(ctx: Context): JSONArray {
        return try {
            val arr     = readArray(logFile(ctx))
            val pending = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (!obj.optBoolean("pushed", false)) pending.put(obj)
            }
            pending
        } catch (_: Exception) { JSONArray() }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /** Mark every stored entry as pushed so they are not re-uploaded on the next launch. */
    fun markAllPushed(ctx: Context) {
        try {
            val file = logFile(ctx)
            val arr  = readArray(file)
            for (i in 0 until arr.length()) arr.getJSONObject(i).put("pushed", true)
            file.writeText(arr.toString())
        } catch (_: Exception) { }
    }

    /** Wipe all entries — used by the debug screen's "Clear Logs" button. */
    fun clearAll(ctx: Context) {
        try { logFile(ctx).writeText("[]") } catch (_: Exception) { }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun rotate(arr: JSONArray) {
        while (arr.length() > MAX_EVENTS) arr.remove(0)  // remove oldest first
    }

    private fun readArray(file: File): JSONArray {
        if (!file.exists()) return JSONArray()
        return try { JSONArray(file.readText()) } catch (_: Exception) { JSONArray() }
    }

    private fun logFile(ctx: Context) = File(ctx.filesDir, LOG_FILE)

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
}
