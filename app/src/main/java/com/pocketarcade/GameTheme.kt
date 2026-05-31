package com.pocketarcade

import android.graphics.Color

data class GameTheme(
    val name: String,
    val swatch: Int,      // color shown in the theme-picker circle
    // Canvas
    val bg: Int,
    val surface: Int,
    // Text
    val text: Int,
    val muted: Int,
    // Game elements
    val player: Int,      // snake head · player paddle · ship
    val rival: Int,       // AI paddle · asteroids · hard bricks
    val accent: Int,      // borders · UI highlights · bullets
    val collect: Int,     // food · ball · powerups · bonus items
    val gridDot: Int,     // subtle grid background dots
    // Overlays
    val overlay: Int,     // ARGB semi-transparent overlay
    val swipeZoneBg: Int, // 0 = transparent (dark mode), or a cream/tinted fill
)

// ── Derived helpers ────────────────────────────────────────────────────────────

fun Int.darken(f: Float): Int = Color.rgb(
    (Color.red(this)   * f).toInt().coerceIn(0, 255),
    (Color.green(this) * f).toInt().coerceIn(0, 255),
    (Color.blue(this)  * f).toInt().coerceIn(0, 255)
)

fun Int.withAlpha(a: Int): Int = Color.argb(a, Color.red(this), Color.green(this), Color.blue(this))

// Snake body colors computed from the player color
val GameTheme.snakeBodyEven: Int get() = player.darken(0.70f)
val GameTheme.snakeBodyOdd:  Int get() = player.darken(0.55f)
val GameTheme.snakeBodyBorder: Int get() = player.darken(0.90f)

// Brick HP color ramp
val GameTheme.brick1: Int get() = player.darken(0.75f)  // hp=1 (softest)
val GameTheme.brick2: Int get() = collect                // hp=2
val GameTheme.brick3: Int get() = rival                  // hp=3 (hardest)

// ── Theme catalogue ────────────────────────────────────────────────────────────

private fun p(hex: String) = Color.parseColor(hex)

object Themes {

    val CLASSIC = Pair(
        GameTheme("Classic", p("#2563eb"),
            bg = p("#0a0d14"), surface = p("#1a1d2e"),
            text = p("#e8ecf0"), muted = p("#6b7a99"),
            player = p("#60a5fa"), rival = p("#f87171"),
            accent = p("#2563eb"), collect = p("#f87171"),
            gridDot = p("#1e3d6e"), overlay = p("#CC0a0d14"),
            swipeZoneBg = 0),
        GameTheme("Classic", p("#2563eb"),
            bg = p("#fdf8f0"), surface = p("#e8eeff"),
            text = p("#0f1117"), muted = p("#64748b"),
            player = p("#1a56d6"), rival = p("#dc2626"),
            accent = p("#1a56d6"), collect = p("#dc2626"),
            gridDot = p("#7090c0"), overlay = p("#CCfdf8f0"),
            swipeZoneBg = p("#dde8fb"))
    )

    val SUNSET = Pair(
        GameTheme("Sunset", p("#f97316"),
            bg = p("#150800"), surface = p("#2a1200"),
            text = p("#fdf0d8"), muted = p("#a08060"),
            player = p("#fb923c"), rival = p("#a78bfa"),
            accent = p("#f97316"), collect = p("#fbbf24"),
            gridDot = p("#5a3010"), overlay = p("#CC150800"),
            swipeZoneBg = 0),
        GameTheme("Sunset", p("#f97316"),
            bg = p("#fff9f2"), surface = p("#ffe8d0"),
            text = p("#1a0800"), muted = p("#8a6040"),
            player = p("#c2410c"), rival = p("#7c3aed"),
            accent = p("#ea580c"), collect = p("#d97706"),
            gridDot = p("#c07848"), overlay = p("#CCfff9f2"),
            swipeZoneBg = p("#fde8d0"))
    )

    val FOREST = Pair(
        GameTheme("Forest", p("#22c55e"),
            bg = p("#040e04"), surface = p("#0a1e0a"),
            text = p("#e8fce8"), muted = p("#6aaa6a"),
            player = p("#4ade80"), rival = p("#f97316"),
            accent = p("#22c55e"), collect = p("#fbbf24"),
            gridDot = p("#1a3a1a"), overlay = p("#CC040e04"),
            swipeZoneBg = 0),
        GameTheme("Forest", p("#22c55e"),
            bg = p("#f5fff5"), surface = p("#d8f8d8"),
            text = p("#041404"), muted = p("#4a8a4a"),
            player = p("#16a34a"), rival = p("#c2410c"),
            accent = p("#15803d"), collect = p("#b45309"),
            gridDot = p("#60a060"), overlay = p("#CCf5fff5"),
            swipeZoneBg = p("#d0f0d0"))
    )

    val CHERRY = Pair(
        GameTheme("Cherry", p("#ec4899"),
            bg = p("#120012"), surface = p("#280028"),
            text = p("#fde8f8"), muted = p("#9a5a9a"),
            player = p("#f472b6"), rival = p("#818cf8"),
            accent = p("#ec4899"), collect = p("#a78bfa"),
            gridDot = p("#4a0a4a"), overlay = p("#CC120012"),
            swipeZoneBg = 0),
        GameTheme("Cherry", p("#ec4899"),
            bg = p("#fff5fb"), surface = p("#ffd8ee"),
            text = p("#14001a"), muted = p("#8a4a7a"),
            player = p("#be185d"), rival = p("#6366f1"),
            accent = p("#db2777"), collect = p("#7c3aed"),
            gridDot = p("#c06090"), overlay = p("#CCfff5fb"),
            swipeZoneBg = p("#fdd0e8"))
    )

    val ICE = Pair(
        GameTheme("Ice", p("#06b6d4"),
            bg = p("#020f15"), surface = p("#072a35"),
            text = p("#d8fafa"), muted = p("#4a8a9a"),
            player = p("#67e8f9"), rival = p("#a78bfa"),
            accent = p("#06b6d4"), collect = p("#34d399"),
            gridDot = p("#0a3a4a"), overlay = p("#CC020f15"),
            swipeZoneBg = 0),
        GameTheme("Ice", p("#06b6d4"),
            bg = p("#f0feff"), surface = p("#c8f4f8"),
            text = p("#010f18"), muted = p("#3a7a8a"),
            player = p("#0891b2"), rival = p("#7c3aed"),
            accent = p("#0e7490"), collect = p("#059669"),
            gridDot = p("#508a9a"), overlay = p("#CCf0feff"),
            swipeZoneBg = p("#c8f0f5"))
    )

    val NEON = Pair(
        GameTheme("Neon", p("#39ff14"),
            bg = p("#000501"), surface = p("#011201"),
            text = p("#c8ffcc"), muted = p("#4aaa4a"),
            player = p("#39ff14"), rival = p("#ff0090"),
            accent = p("#00e040"), collect = p("#ffe600"),
            gridDot = p("#0a2a0a"), overlay = p("#CC000501"),
            swipeZoneBg = 0),
        GameTheme("Neon", p("#39ff14"),
            bg = p("#f0fff0"), surface = p("#c8eec8"),
            text = p("#000a00"), muted = p("#3a8a3a"),
            player = p("#16a34a"), rival = p("#be185d"),
            accent = p("#15803d"), collect = p("#ca8a04"),
            gridDot = p("#50a050"), overlay = p("#CCf0fff0"),
            swipeZoneBg = p("#c0ecc0"))
    )

    /** All pairs in order. Index is persisted in prefs. */
    val ALL = listOf(CLASSIC, SUNSET, FOREST, CHERRY, ICE, NEON)
}

// ── App background themes ──────────────────────────────────────────────────────
// Muted/desaturated dark backgrounds for the app shell (menus, settings, etc.).
// One per game-board theme, loosely matching its visual identity.

data class AppBgTheme(val name: String, val swatch: Int, val bg: Int)

object AppBgThemes {
    val ALL = listOf(
        AppBgTheme("Void",  p("#2a3a52"), p("#0d1117")),  // Classic
        AppBgTheme("Ember", p("#4a2a10"), p("#1a0e05")),  // Sunset
        AppBgTheme("Grove", p("#183a14"), p("#080e06")),  // Forest
        AppBgTheme("Dusk",  p("#3a1038"), p("#100810")),  // Cherry
        AppBgTheme("Frost", p("#082a3c"), p("#060d16")),  // Ice
        AppBgTheme("Ink",   p("#1a2a1c"), p("#040a04"))   // Neon
    )
}
