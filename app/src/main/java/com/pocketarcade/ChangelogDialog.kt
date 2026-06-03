package com.pocketarcade

import android.app.Dialog
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.storage.PrefsManager

private val CHANGELOG = mapOf(
    7 to """★ Friends leaderboard — weekly & monthly
  Scores now track what you played THIS
  week / month, not your all-time best

★ Start game button on the board
  Pong & Brick Breaker now show a
  START GAME button over the game
  board after picking difficulty

★ What's New shows version number
  Title now reads "WHAT'S NEW — v1.5.1"

★ Global leaderboard icon alignment
  Game icons & labels are now centered
  together on picker tiles and tab chips

★ Ad-free popup timing improved
  "Go Ad Free" only appears when you
  actually finish a game, not on every
  return to the main menu

★ Auto version bump on every build
  CI now bumps the patch version each
  time code is pushed to master""",

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

    6 to """★ Asteroids controls restored
  Joystick feel is back to original

★ Pong — Start button added
  Game no longer starts on difficulty
  select; ball goes to AI first so
  you can get your bearings

★ Difficulty locked mid-game
  Pong & Brick Breaker difficulty
  cannot change once a game starts

★ Pong best-score format
  High score shows your best match
  (e.g. 7–3); difficulty shown during play

★ Pong W/L ratio fixed
  Profile no longer shows "inf"

★ Brick Breaker difficulty label
  Shown next to level during play

★ Brick Breaker time reset
  Inflated demo playtime cleared to zero

★ Back button polished
  Arrow replaced with clean chevron,
  aligned across all games

★ Asteroids leaderboard column
  Score column widened for 7-digit scores

★ Leaderboards globe button
  Moved to bottom nav bar as a
  globe icon circle

★ Global leaderboard — best score only
  Single best score per game mode shown

★ Personal tab added
  See your on-device high scores in
  the global leaderboard

★ Game icons colorized
  Leaderboard game select icons now
  colorized and center-aligned

★ Friends — tap your own name
  Opens your profile directly; no longer
  requires the Edit button first

★ Game icons on friends leaderboard
  Colorized independently from mutual
  friend indicators

★ Friend profile best scores
  Clean two-column layout

★ Main menu score formatting
  High scores now show commas
  (e.g. 52,847)

★ App background themes visible
  Selected theme now produces a clearly
  noticeable color change

★ Theme picker split-oval swatches
  Shows both primary and secondary color;
  theme selector moved to top of each
  game's settings menu

★ Scrollable What's New & Settings
  Long content no longer pushes the
  close button off screen""",

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
    view.findViewById<TextView>(R.id.tvChangelogHeader).text = "WHAT'S NEW — v${BuildConfig.VERSION_NAME}"
    view.findViewById<TextView>(R.id.tvChangelog).text = log

    // Cap the scroll area height so the close button is always reachable.
    val scrollView = view.findViewById<ScrollView>(R.id.scrollChangelog)
    val maxScrollDp = 240f
    scrollView.post {
        val maxPx = (maxScrollDp * activity.resources.displayMetrics.density).toInt()
        if (scrollView.height > maxPx) {
            val lp = scrollView.layoutParams
            lp.height = maxPx
            scrollView.layoutParams = lp
        }
    }

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
