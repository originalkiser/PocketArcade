package com.pocketarcade

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.leaderboard.LeaderboardManager
import com.pocketarcade.storage.PrefsManager

class RecordBookActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_book)
        ThemeManager.applyWindowBackground(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val totalMissions = listOf(
            PrefsManager.GAME_SNAKE, PrefsManager.GAME_PONG,
            PrefsManager.GAME_ASTEROIDS, PrefsManager.GAME_BRICKBREAKER
        ).sumOf { PrefsManager.getStatPlays(this, it) }
        findViewById<TextView>(R.id.tvTotalMissions).text = "$totalMissions TOTAL MISSIONS"

        val container = findViewById<LinearLayout>(R.id.contentContainer)

        // ── Overall summary ───────────────────────────────────────────────────────
        addSection(container, "OVERALL")
        addMostPlayed(container)
        addRow(container, "TOTAL MISSIONS", totalMissions.toString())
        val totalMs = listOf(
            PrefsManager.GAME_SNAKE, PrefsManager.GAME_PONG,
            PrefsManager.GAME_ASTEROIDS, PrefsManager.GAME_BRICKBREAKER
        ).sumOf { PrefsManager.getStatTotalTimeMs(this, it) }
        addRow(container, "TIME IN FIELD", formatDuration(totalMs))
        addSpacer(container)

        // ── Snake ─────────────────────────────────────────────────────────────────
        buildGameSection(container,
            game       = PrefsManager.GAME_SNAKE,
            label      = "SNAKE",
            accentHex  = "#4f8ef7",
            modes      = emptyList()
        )
        val fruits = PrefsManager.getSnakeFruits(this)
        val snakePlays = PrefsManager.getStatPlays(this, PrefsManager.GAME_SNAKE)
        val ratio = if (snakePlays > 0) "%.1f".format(fruits.toFloat() / snakePlays) else "-"
        addRow(container, "FRUITS CONSUMED", fruits.toString(), "#4f8ef7")
        addRow(container, "FRUITS/MISSION",  ratio,             "#4f8ef7")
        addSpacer(container)
        buildLeaderboard(container, PrefsManager.GAME_SNAKE, "#4f8ef7")
        addSpacer(container)

        // ── Pong ──────────────────────────────────────────────────────────────────
        buildGameSection(container,
            game      = PrefsManager.GAME_PONG,
            label     = "PONG",
            accentHex = "#ef5350",
            modes     = listOf("easy", "medium", "hard")
        )
        addSpacer(container)
        buildLeaderboard(container, PrefsManager.GAME_PONG, "#ef5350")
        addSpacer(container)

        // ── Asteroids ─────────────────────────────────────────────────────────────
        buildGameSection(container,
            game      = PrefsManager.GAME_ASTEROIDS,
            label     = "ASTEROIDS",
            accentHex = "#00d4ff",
            modes     = emptyList()
        )
        addSpacer(container)
        buildLeaderboard(container, PrefsManager.GAME_ASTEROIDS, "#00d4ff")
        addSpacer(container)

        // ── Brick Breaker ─────────────────────────────────────────────────────────
        buildGameSection(container,
            game      = PrefsManager.GAME_BRICKBREAKER,
            label     = "BRICK BREAKER",
            accentHex = "#f1c40f",
            modes     = listOf("easy", "medium", "hard")
        )
        addSpacer(container)
        buildLeaderboard(container, PrefsManager.GAME_BRICKBREAKER, "#f1c40f")
        addSpacer(container)
    }

    // ── Section builders ──────────────────────────────────────────────────────────

    private fun buildGameSection(
        container: LinearLayout, game: String, label: String,
        accentHex: String, modes: List<String>
    ) {
        addSection(container, label, accentHex)

        val plays    = PrefsManager.getStatPlays(this, game)
        val total    = PrefsManager.getStatTotalScore(this, game)
        val timeMs   = PrefsManager.getStatTotalTimeMs(this, game)
        val hi       = PrefsManager.getHighScore(this, game)
        val avg      = if (plays > 0) total / plays else 0L
        val avgTime  = if (plays > 0) timeMs / plays else 0L

        addRow(container, "MISSIONS LOGGED", plays.toString(), accentHex)
        addRow(container, "TOP SCORE",       hi.toString(),   accentHex)
        addRow(container, "AVG SCORE",       avg.toString(),  accentHex)
        addRow(container, "TOTAL TIME",      formatDuration(timeMs))
        addRow(container, "AVG SESSION",     formatDuration(avgTime))

        if (modes.isNotEmpty()) {
            addDivider(container, "#333344")
            // Mode breakdown
            val mostPlayedMode = modes.maxByOrNull { PrefsManager.getStatPlays(this, game, it) }
            modes.forEach { mode ->
                val mp = PrefsManager.getStatPlays(this, game, mode)
                val mt = PrefsManager.getStatTotalScore(this, game, mode)
                val ma = if (mp > 0) mt / mp else 0L
                val star = if (mode == mostPlayedMode && mp > 0) " ★" else ""
                addSubHeader(container, mode.uppercase() + star)
                addRow(container, "  MISSIONS",  mp.toString(),  accentHex)
                addRow(container, "  AVG SCORE", ma.toString(),  accentHex)
                addRow(container, "  TIME",      formatDuration(PrefsManager.getStatTotalTimeMs(this, game, mode)))
            }
        }
    }

    private fun buildLeaderboard(container: LinearLayout, game: String, accentHex: String) {
        val entries = LeaderboardManager.getEntries(this, game)
        if (entries.isEmpty()) return
        addSubHeader(container, "TOP SCORES")
        entries.forEachIndexed { i, e ->
            val rank = "#${i + 1}"
            addLeaderboardRow(container, rank, e.initials.trim(), e.score.toString(), e.formattedDate, accentHex)
        }
    }

    // ── Most played ───────────────────────────────────────────────────────────────

    private fun addMostPlayed(container: LinearLayout) {
        data class Candidate(val label: String, val plays: Int, val accent: String)

        val candidates = listOf(
            Candidate("SNAKE",        PrefsManager.getStatPlays(this, PrefsManager.GAME_SNAKE),        "#4f8ef7"),
            Candidate("PONG",         PrefsManager.getStatPlays(this, PrefsManager.GAME_PONG),         "#ef5350"),
            Candidate("ASTEROIDS",    PrefsManager.getStatPlays(this, PrefsManager.GAME_ASTEROIDS),    "#00d4ff"),
            Candidate("BRICK BREAKER",PrefsManager.getStatPlays(this, PrefsManager.GAME_BRICKBREAKER),"#f1c40f")
        )
        val top = candidates.maxByOrNull { it.plays }
        if (top != null && top.plays > 0) {
            addRow(container, "MOST PLAYED", top.label, top.accent)
        }

        // Mode favorites within Pong and BB
        val pongModes = listOf("easy", "medium", "hard")
        val topPongMode = pongModes.maxByOrNull { PrefsManager.getStatPlays(this, PrefsManager.GAME_PONG, it) }
        val topPongPlays = if (topPongMode != null) PrefsManager.getStatPlays(this, PrefsManager.GAME_PONG, topPongMode) else 0
        if (topPongPlays > 0) addRow(container, "FAV PONG MODE", topPongMode!!.uppercase(), "#ef5350")

        val bbModes = listOf("easy", "medium", "hard")
        val topBBMode = bbModes.maxByOrNull { PrefsManager.getStatPlays(this, PrefsManager.GAME_BRICKBREAKER, it) }
        val topBBPlays = if (topBBMode != null) PrefsManager.getStatPlays(this, PrefsManager.GAME_BRICKBREAKER, topBBMode) else 0
        if (topBBPlays > 0) addRow(container, "FAV BB MODE", topBBMode!!.uppercase(), "#f1c40f")
    }

    // ── View helpers ──────────────────────────────────────────────────────────────

    private fun addSection(container: LinearLayout, title: String, accentHex: String = "#AA44FF") {
        val tv = TextView(this).apply {
            text = "═══  $title  ═══"
            setTextColor(android.graphics.Color.parseColor(accentHex))
            textSize = 9f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 8)
        }
        container.addView(tv)
        addDivider(container, accentHex)
    }

    private fun addSubHeader(container: LinearLayout, title: String) {
        val tv = TextView(this).apply {
            text = title
            setTextColor(android.graphics.Color.parseColor("#AA44FF"))
            textSize = 7f
            setPadding(0, 12, 0, 4)
        }
        container.addView(tv)
    }

    private fun addRow(
        container: LinearLayout,
        label: String,
        value: String,
        valueColorHex: String = "#CCCCCC"
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
        }
        val tvLabel = TextView(this).apply {
            text = label
            setTextColor(android.graphics.Color.parseColor("#666688"))
            textSize = 6f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvValue = TextView(this).apply {
            text = value
            setTextColor(android.graphics.Color.parseColor(valueColorHex))
            textSize = 6f
            gravity = Gravity.END
        }
        row.addView(tvLabel)
        row.addView(tvValue)
        container.addView(row)
    }

    private fun addLeaderboardRow(
        container: LinearLayout,
        rank: String, initials: String, score: String, date: String, accentHex: String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 3, 0, 3)
        }
        fun tv(text: String, color: String, size: Float, weight: Float = 0f) = TextView(this).apply {
            this.text = text
            setTextColor(android.graphics.Color.parseColor(color))
            textSize = size
            layoutParams = if (weight > 0f)
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            else
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(tv(rank,     accentHex, 5f))
        row.addView(tv("  ",     "#000000", 5f))
        row.addView(tv(initials, "#FFFFFF",  5f, 1f))
        row.addView(tv(score,    accentHex, 5f))
        row.addView(tv("  $date","#444466", 5f))
        container.addView(row)
    }

    private fun addDivider(container: LinearLayout, colorHex: String) {
        val v = android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor(colorHex))
            alpha = 0.3f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 4, 0, 4) }
        }
        container.addView(v)
    }

    private fun addSpacer(container: LinearLayout) {
        val v = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16)
        }
        container.addView(v)
    }

    // ── Formatting ────────────────────────────────────────────────────────────────

    private fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0s"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0  -> "${h}h ${m}m"
            m > 0  -> "${m}m ${sec}s"
            else   -> "${sec}s"
        }
    }
}
