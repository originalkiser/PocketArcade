package com.pocketarcade

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class IconRotationWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        rotateToNextIcon(applicationContext)
        return Result.success()
    }

    companion object {

        private const val PREFS_NAME  = "icon_rotation"
        private const val KEY_INDEX   = "icon_index"
        private const val WORK_NAME   = "icon_rotation"

        // Must match <activity-alias android:name> in the manifest (short class form)
        private val ALIASES = listOf(
            ".icon.Arcade",
            ".icon.Snake",
            ".icon.Pong",
            ".icon.Asteroids",
            ".icon.BrickBreaker"
        )

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<IconRotationWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints.NONE)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun rotateToNextIcon(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getInt(KEY_INDEX, 0)
            val next = (current + 1) % ALIASES.size

            val pm = context.packageManager
            val pkg = context.packageName

            // Enable next icon first so there is always at least one active launcher entry
            val enableTarget = ComponentName(pkg, pkg + ALIASES[next])
            try {
                pm.setComponentEnabledSetting(
                    enableTarget,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {
                return  // If we can't enable the next one, don't disable anything
            }

            ALIASES.forEachIndexed { index, alias ->
                if (index != next) {
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(pkg, pkg + alias),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    } catch (_: Exception) {}
                }
            }

            prefs.edit().putInt(KEY_INDEX, next).apply()
        }
    }
}
