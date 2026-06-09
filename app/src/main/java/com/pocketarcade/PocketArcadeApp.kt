package com.pocketarcade

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pocketarcade.logging.CrashReporter
import com.pocketarcade.logging.ErrorLogger
import com.pocketarcade.storage.PrefsManager

/**
 * Application subclass.
 *
 * Responsibilities:
 *  1. Initialise Crashlytics custom keys.
 *  2. Flush any locally buffered error logs to Firebase (offline sync).
 *  3. Install a global uncaught-exception handler that writes the crash
 *     to the local log before delegating to the original handler so the
 *     app still crashes normally — nothing is suppressed.
 */
class PocketArcadeApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Apply the stored display mode (Dark/Light/System) before any Activity
        //    is created so the DayNight configuration is correct on first render.
        ThemeManager.applyDisplayMode(this)

        // 2. Force-enable Crashlytics — the modern SDK silently disables collection
        //    in debug builds unless explicitly opted in.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        // 4. Attach app-wide Crashlytics keys (version, uid, current screen).
        CrashReporter.init(this)

        // 5. Push any locally stored errors from previous sessions.
        CrashReporter.syncLocalLogs(this)

        // 6. Install global uncaught-exception handler.
        val upstream = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log locally first — synchronous so it completes before the process dies.
            try {
                ErrorLogger.log(
                    ctx        = this,
                    type       = throwable.javaClass.simpleName,
                    message    = throwable.message ?: "(no message)",
                    stackTrace = throwable.stackTraceToString(),
                    screen     = CrashReporter.currentScreen,
                    userId     = PrefsManager.getGlobalUsername(this) ?: ""
                )
            } catch (_: Throwable) { /* must never throw inside the crash handler */ }

            // Delegate to the original handler (includes Crashlytics + system crash).
            // The app still crashes as the user expects.
            upstream?.uncaughtException(thread, throwable)
        }
    }
}
