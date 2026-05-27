package com.pocketarcade

import android.app.Activity
import android.content.Intent
import com.pocketarcade.storage.PrefsManager

object ShareUtils {

    private const val DOWNLOAD_URL =
        "https://github.com/originalkiser/PocketArcade/releases/latest/download/PocketArcade.apk"

    fun shareScore(activity: Activity, game: String, score: Int) {
        val text = buildShareText(game, score)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, "Share your score"))
    }

    private fun buildShareText(game: String, score: Int): String {
        val (emoji, label, maxScore) = when (game) {
            PrefsManager.GAME_SNAKE        -> Triple("🐍", "SNAKE",        100)
            PrefsManager.GAME_PONG         -> Triple("🏓", "PONG",         10)
            PrefsManager.GAME_ASTEROIDS    -> Triple("🚀", "ASTEROIDS",    10_000)
            PrefsManager.GAME_BRICKBREAKER -> Triple("🧱", "BRICK BREAKER", 5_000)
            else                           -> Triple("🕹️", game.uppercase(), 100)
        }

        val ratio  = (score.toFloat() / maxScore).coerceIn(0f, 1f)
        val filled = (ratio * 10).toInt()
        val bar    = "█".repeat(filled) + "░".repeat(10 - filled)

        return buildString {
            appendLine("🕹️ POCKET ARCADE")
            appendLine("$emoji $label  ·  $score pts")
            appendLine()
            appendLine(bar)
            appendLine()
            appendLine("Think you can beat me? 👾")
            appendLine()
            append(DOWNLOAD_URL)
        }
    }
}
