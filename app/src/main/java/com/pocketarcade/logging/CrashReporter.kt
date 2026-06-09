package com.pocketarcade.logging

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pocketarcade.BuildConfig
import com.pocketarcade.storage.PrefsManager

/**
 * Thin wrapper around Firebase Crashlytics.
 *
 * Call [setScreen] whenever the user navigates to a new screen / game — it is
 * attached as a custom key to every subsequent Crashlytics report.
 *
 * Call [syncLocalLogs] once at app launch to flush any locally buffered errors
 * (captured offline or before Crashlytics could upload) as non-fatal events.
 */
object CrashReporter {

    @Volatile private var _currentScreen: String = "unknown"

    /** The last screen set via [setScreen] — exposed for the crash handler in Application. */
    val currentScreen: String get() = _currentScreen

    // ── Init ──────────────────────────────────────────────────────────────────

    /**
     * Attaches app-wide custom keys. Call once from Application.onCreate()
     * after Firebase is ready.
     */
    fun init(ctx: Context) {
        safeFirebase {
            setCustomKey("app_version", BuildConfig.VERSION_NAME)
            setCustomKey("screen",      _currentScreen)
            val uid = PrefsManager.getGlobalUsername(ctx) ?: "anonymous"
            setUserId(uid)
            setCustomKey("user_id", uid)
        }
    }

    // ── Screen tracking ───────────────────────────────────────────────────────

    /** Call this from every Activity's onResume (or game start). */
    fun setScreen(screen: String) {
        _currentScreen = screen
        safeFirebase { setCustomKey("screen", screen) }
    }

    // ── Error recording ───────────────────────────────────────────────────────

    /**
     * Records a caught (non-fatal) exception both locally and in Crashlytics.
     * Safe to call from any thread.
     */
    fun recordNonFatal(ctx: Context, throwable: Throwable, screen: String = _currentScreen) {
        ErrorLogger.log(
            ctx        = ctx,
            type       = throwable.javaClass.simpleName,
            message    = throwable.message ?: "(no message)",
            stackTrace = throwable.stackTraceToString(),
            screen     = screen,
            userId     = PrefsManager.getGlobalUsername(ctx) ?: ""
        )
        safeFirebase { recordException(throwable) }
    }

    /**
     * Convenience overload: synthesises a [RuntimeException] from a [tag] + [message]
     * so it surfaces as a distinct issue in Crashlytics.
     */
    fun recordError(ctx: Context, tag: String, message: String, screen: String = _currentScreen) {
        recordNonFatal(ctx, RuntimeException("[$tag] $message"), screen)
    }

    // ── Offline sync ──────────────────────────────────────────────────────────

    /**
     * On each app launch: push any locally stored entries that were never sent
     * (e.g. captured while offline or during a fatal crash) as non-fatal events.
     *
     * Runs on a daemon thread — never blocks the UI.
     * On failure (Firebase unavailable) the local logs are untouched and retried
     * on the next launch.
     */
    fun syncLocalLogs(ctx: Context) {
        Thread({
            try {
                val pending = ErrorLogger.getPending(ctx)
                if (pending.length() == 0) return@Thread
                val fc = FirebaseCrashlytics.getInstance()
                for (i in 0 until pending.length()) {
                    val obj     = pending.getJSONObject(i)
                    val synth   = Exception(buildOfflineMessage(obj))
                    fc.recordException(synth)
                }
                // Only mark pushed after the loop — if any recordException threw, we retry.
                ErrorLogger.markAllPushed(ctx)
            } catch (_: Exception) {
                // Firebase unavailable — local logs kept for next launch.
            }
        }, "pa-crashlytics-sync").also { it.isDaemon = true }.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildOfflineMessage(obj: org.json.JSONObject): String = buildString {
        append("[OFFLINE_LOG] ")
        append(obj.optString("type"))
        append(": ")
        append(obj.optString("message"))
        append("\nScreen: ").append(obj.optString("screen"))
        append("\nDevice: ").append(obj.optString("device"))
        append("\nOS:     ").append(obj.optString("os_version"))
        append("\nAt:     ").append(obj.optString("timestamp"))
        val stack = obj.optString("stack")
        if (stack.isNotEmpty()) append("\n\n").append(stack)
    }

    /**
     * Executes [block] on the FirebaseCrashlytics instance, swallowing any
     * exception so that crash-reporting logic never itself causes a crash.
     */
    private inline fun safeFirebase(block: FirebaseCrashlytics.() -> Unit) {
        try { FirebaseCrashlytics.getInstance().block() } catch (_: Exception) { }
    }
}
