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

★ Share link now opens releases page""",

    4 to """★ Brick Breaker lives system
  3 lives on Easy & Hard, 2 on Medium
  Hearts displayed next to your score

★ Extra Life power-up (level 3+)
  ♥ drops from bricks — grab it!

★ Brick Breaker back button
  ← in the top bar + ← MENU on the
  difficulty screen to return to menu

★ Ball tunneling fix (diagonal pass)
  Ball can no longer slip through the
  paddle at a steep angle

★ Asteroids lives shown as ♥ ♥ ♥

★ Fake ads only — no test ad network
  connections until AdMob is live

★ What's New in Settings
  View this changelog any time""",

    5 to """★ Global Leaderboard
  Compete with players worldwide!
  Sign up from Profile or Settings

★ Friends system
  Add friends, compare scores,
  send & accept friend requests

★ Profile page
  Set your username, country/state,
  and upload a custom photo

★ App Background Themes
  6 new dark palettes (Void, Ember,
  Grove, Dusk, Frost, Ink)

★ Game Board Themes in Settings
  Set one theme for all games at once

★ Asteroids controls reworked
  Rotation and thrust are now
  independent — cleaner handling

★ Time tracking bug fixed
  Demo mode no longer logged
  hundreds of thousands of hours

★ Seamless Record Book scroll
  Game tabs now loop without hitches"""
)

fun showChangelogIfNeeded(activity: AppCompatActivity, onDone: () -> Unit = {}) {
    val lastCode = PrefsManager.getLastVersionCode(activity)
    val current  = BuildConfig.VERSION_CODE
    if (lastCode == current) { onDone(); return }
    PrefsManager.setLastVersionCode(activity, current)

    val log = CHANGELOG[current] ?: run { onDone(); return }
    showChangelogDialog(activity, log, onDone)
}

fun showChangelogDialog(activity: AppCompatActivity) {
    val log = CHANGELOG[BuildConfig.VERSION_CODE] ?: return
    showChangelogDialog(activity, log)
}

private fun showChangelogDialog(activity: AppCompatActivity, log: String, onDone: () -> Unit = {}) {
    val view = activity.layoutInflater.inflate(R.layout.dialog_changelog, null)
    view.findViewById<TextView>(R.id.tvChangelogVersion).text = "v${BuildConfig.VERSION_NAME}"
    view.findViewById<TextView>(R.id.tvChangelog).text = log

    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    dialog.setOnDismissListener { onDone() }
    view.findViewById<TextView>(R.id.btnChangelogClose).setOnClickListener { dialog.dismiss() }
    dialog.show()
}
