package com.pocketarcade.leaderboard

import android.content.Context
import com.pocketarcade.storage.PrefsManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class LeaderboardEntry(
    val score: Int,
    val date: Long,
    val initials: String
) {
    val formattedDate: String
        get() = SimpleDateFormat("MM/dd/yy", Locale.US).format(Date(date))
}

object LeaderboardManager {

    private const val MAX_ENTRIES = 10
    private val fmt = SimpleDateFormat("MM/dd/yy", Locale.US)

    private fun key(game: String) = "leaderboard_$game"

    fun getEntries(ctx: Context, game: String): List<LeaderboardEntry> {
        val json = ctx.getSharedPreferences("pocket_arcade_prefs", Context.MODE_PRIVATE)
            .getString(key(game), null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                LeaderboardEntry(
                    score    = obj.getInt("score"),
                    date     = obj.getLong("date"),
                    initials = obj.optString("initials", "   ")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isTopTen(ctx: Context, game: String, score: Int): Boolean {
        val entries = getEntries(ctx, game)
        return entries.size < MAX_ENTRIES || score > (entries.minOfOrNull { it.score } ?: -1)
    }

    fun addEntry(ctx: Context, game: String, score: Int, initials: String): Int {
        val entries = getEntries(ctx, game).toMutableList()
        val entry = LeaderboardEntry(score, System.currentTimeMillis(), initials.take(3).padEnd(3))
        entries.add(entry)
        entries.sortByDescending { it.score }
        val trimmed = entries.take(MAX_ENTRIES)
        val rank = trimmed.indexOfFirst { it.score == entry.score && it.date == entry.date } + 1

        val arr = JSONArray()
        trimmed.forEach { e ->
            arr.put(JSONObject().apply {
                put("score", e.score)
                put("date", e.date)
                put("initials", e.initials)
            })
        }
        ctx.getSharedPreferences("pocket_arcade_prefs", Context.MODE_PRIVATE)
            .edit().putString(key(game), arr.toString()).apply()

        return if (rank > 0) rank else -1
    }
}
