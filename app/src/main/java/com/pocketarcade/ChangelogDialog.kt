package com.pocketarcade

import android.app.Dialog
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.storage.PrefsManager

private val CHANGELOG = mapOf(
    3 to """★ Brick Breaker difficulty settings
  Easy / Medium / Hard ball speeds

★ Pong ball clipping fixed
  Ball no longer tunnels through paddles

★ Asteroids controls revamped
  Bigger joystick + fire button, 50% alpha,
  auto-fire tooltip when enabled

★ App icons resized
  Less clipping, full icon visible

★ Leaderboard "Don't Add" restyled
  Matches the rest of the app

★ Share game button in Settings

★ Swipe-to-restart removed in Snake
  Restart only via the RESTART button

★ Settings / Credits text enlarged

★ 20 more house ads

★ Light mode now applies to full app

★ Share link now opens releases page"""
)

fun showChangelogIfNeeded(activity: AppCompatActivity) {
    val lastCode = PrefsManager.getLastVersionCode(activity)
    val current  = BuildConfig.VERSION_CODE
    if (lastCode == current) return
    PrefsManager.setLastVersionCode(activity, current)

    val log = CHANGELOG[current] ?: return  // no entry = no popup

    val view = activity.layoutInflater.inflate(R.layout.dialog_changelog, null)
    view.findViewById<TextView>(R.id.tvChangelogVersion).text = "v${BuildConfig.VERSION_NAME}"
    view.findViewById<TextView>(R.id.tvChangelog).text = log

    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    view.findViewById<TextView>(R.id.btnChangelogClose).setOnClickListener { dialog.dismiss() }
    dialog.show()
}
