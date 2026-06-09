# Pixel Parlor — Changelog

---

## v2.1.0 (versionCode 49)

### Leaderboard Fixes
- **Cave Diver global & all-time scores corrected** — Score updates used a Firestore transaction with no failure handler; if the write failed silently the old lower score persisted forever. Fixed with a batch write + fallback, plus duplicate-entry cleanup on every submit.
- **Single best score per player** — World and Local leaderboards now deduplicate by uid client-side so no player ever appears more than once even if stale duplicate documents exist in Firestore.
- **"ALL" difficulty tab removed** — Brick Breaker, Block Drop, and Memory Match now open directly on the Easy tab. Each difficulty has its own ranked list with no combined view.

### Leaderboard UI
- **Memory Match shows moves** — Leaderboard entries for Memory Match display "X moves" (e.g. "24 moves") instead of a raw point total. Fewer moves = better rank.
- **Cave Diver uses ship-silhouette icon** — The game selector chip in the global leaderboard now shows the same horizontal ship icon used in the friends-list rank chips, instead of the full scene icon that rendered as a solid tinted block.

### Game Changes
- **Asteroids direct-movement controls** — The joystick now moves the ship in the exact direction it's pushed (left = left, right = right, up = up, down = down). The ship rotates to face its movement direction. Replaces the old rotate-then-thrust model.

### Icons
- **Cave Diver main menu icon** — Removed the solid dark background rectangles and cockpit dark-fill ellipse. Icon is now transparent-background, consistent with all other game icons.

---

## v1.4.0 (versionCode 5)

### New Features
- **Global Leaderboard** — Compete with players worldwide. Sign up from the Profile page or Settings.
- **Friends System** — Add friends by username, accept/decline requests, and compare scores side-by-side.
- **Profile Page** — Set your display name, country/state, choose an avatar (emoji + color), or upload a custom photo.
- **App Background Themes** — 6 new muted dark palettes for the app shell: Void, Ember, Grove, Dusk, Frost, Ink. Separate from the game board theme.
- **Game Board Theme in main Settings** — Choose one theme that applies to all four game boards at once from the Settings page.

### Bug Fixes
- **Asteroids controls reworked** — Rotation and thrust are now independent. The ship rotates cleanly via the horizontal joystick axis; thrust fires in the ship's current facing direction. Previously both were snapped from the joystick angle.
- **Time tracking bug fixed** — Demo mode games were logging ~494,000 hours of play time because `gameStartTime` was never set for demos but game-over handlers ran unconditionally, recording `currentTimeMillis() - 0L` as the duration.
- **Demo restart regression fixed** — Asteroids, Brick Breaker, and Pong demo games now auto-restart 2 seconds after ending. The `!demoMode` guard on the game-over callbacks was preventing the restart loop from running.
- **Record Book seamless scroll** — The game-tab scroll bar no longer hitches mid-fling. Fixed by using 5 copies instead of 3 and idle-detection repositioning (150 ms delay) rather than calling `scrollTo()` during an active fling.
- **Per-game "Apply to all" toggle removed** — The per-game theme dialog and Snake settings no longer show the confusing "apply to all games" toggle. Global theme is now set exclusively from the main Settings page.

---

## v1.3.0 (versionCode 3)

- Brick Breaker difficulty settings (Easy / Medium / Hard ball speeds)
- Pong ball clipping fixed — ball no longer tunnels through paddles
- Asteroids controls revamped — bigger joystick + fire button, 50% alpha, auto-fire tooltip
- App icons resized — less clipping, full icon visible
- Leaderboard "Don't Add" restyled to match the rest of the app
- Share game button in Settings
- Swipe-to-restart removed in Snake (restart only via RESTART button)
- Settings / Credits text enlarged
- Light mode now applies to full app
- Share link now opens releases page

---

## v1.2.0 (versionCode 2)

- Brick Breaker lives system (3 lives Easy/Hard, 2 Medium); hearts shown next to score
- Extra Life power-up (♥) drops from bricks at level 3+
- Brick Breaker back button (← in top bar; ← MENU on difficulty screen)
- Ball diagonal-pass tunneling fix — ball can no longer slip through paddle at steep angles
- Asteroids lives displayed as ♥ ♥ ♥
- What's New dialog in Settings

---

## v1.1.0 (versionCode 1)

- Initial public release
- Snake, Pong, Asteroids, Brick Breaker
- Local leaderboard with initials entry
- Color themes (Classic, Sunset, Forest, Cherry, Ice, Neon)
- Demo mode on main screen
- Record Book with per-game stats
- AdMob banner + ad-free IAP
