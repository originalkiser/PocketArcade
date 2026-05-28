package com.pocketarcade

import android.app.Activity
import android.content.Intent
import com.pocketarcade.storage.PrefsManager

object ShareUtils {

    private const val DOWNLOAD_URL =
        "https://github.com/originalkiser/PocketArcade/releases/latest"

    fun shareApp(activity: Activity) {
        val text = "🕹️ POCKET ARCADE\n\nSnake · Pong · Asteroids · Brick Breaker\n\nAll the classics, free!\n\n$DOWNLOAD_URL"
        activity.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) },
                "Share Pocket Arcade"
            )
        )
    }

    fun shareScore(activity: Activity, game: String, score: Int, date: String = "", mode: String? = null) {
        val text = buildShareText(game, score, date, mode)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, "Share your score"))
    }

    private fun buildShareText(game: String, score: Int, date: String, mode: String? = null): String {
        val (emoji, label) = when (game) {
            PrefsManager.GAME_SNAKE        -> "🐍" to "SNAKE"
            PrefsManager.GAME_PONG         -> "🏓" to "PONG"
            PrefsManager.GAME_ASTEROIDS    -> "🚀" to "ASTEROIDS"
            PrefsManager.GAME_BRICKBREAKER -> "🧱" to "BRICK BREAKER"
            else                           -> "🕹️" to game.uppercase()
        }

        val modeLabel = if (mode != null) " [${mode.uppercase()}]" else ""

        val scoreText = when (game) {
            PrefsManager.GAME_PONG -> {
                if (score >= 10) {
                    val ps = score / 10; val ai = score % 10
                    if (ps >= 7) "Won $ps-$ai vs the AI!" else "Lost $ps-$ai to the AI"
                } else "$score pts"
            }
            else -> "$score pts"
        }

        val dateSuffix = if (date.isNotEmpty()) "  ·  $date" else ""

        return buildString {
            appendLine("🕹️ POCKET ARCADE")
            appendLine()
            appendLine("$emoji $label$modeLabel")
            appendLine("$scoreText$dateSuffix")
            appendLine()
            appendLine("Think you can beat me? 👾")
            appendLine()
            append(DOWNLOAD_URL)
        }
    }
}
