package com.pocketarcade

import android.app.Activity
import android.content.Intent
import com.pocketarcade.storage.PrefsManager

object ShareUtils {

    private const val DOWNLOAD_URL =
        "https://github.com/originalkiser/PocketArcade/releases/latest/download/PocketArcade.apk"

    fun shareScore(activity: Activity, game: String, score: Int, date: String = "") {
        val text = buildShareText(game, score, date)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, "Share your score"))
    }

    private fun buildShareText(game: String, score: Int, date: String): String {
        val (emoji, label) = when (game) {
            PrefsManager.GAME_SNAKE        -> "🐍" to "SNAKE"
            PrefsManager.GAME_PONG         -> "🏓" to "PONG"
            PrefsManager.GAME_ASTEROIDS    -> "🚀" to "ASTEROIDS"
            PrefsManager.GAME_BRICKBREAKER -> "🧱" to "BRICK BREAKER"
            else                           -> "🕹️" to game.uppercase()
        }

        val scoreText = when (game) {
            PrefsManager.GAME_PONG -> "Beat the AI!"
            else                   -> "$score pts"
        }

        val dateSuffix = if (date.isNotEmpty()) "  ·  $date" else ""

        return buildString {
            appendLine("🕹️ POCKET ARCADE")
            appendLine()
            appendLine("$emoji $label")
            appendLine("$scoreText$dateSuffix")
            appendLine()
            appendLine("Think you can beat me? 👾")
            appendLine()
            append(DOWNLOAD_URL)
        }
    }
}
