package com.pocketarcade

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.leaderboard.LeaderboardManager
import com.pocketarcade.storage.PrefsManager

class RecordBookActivity : AppCompatActivity() {

    private lateinit var contentContainer: LinearLayout
    private var activeTab = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_book)
        ThemeManager.applyWindowBackground(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val totalGames = listOf(
            PrefsManager.GAME_SNAKE, PrefsManager.GAME_PONG,
            PrefsManager.GAME_ASTEROIDS, PrefsManager.GAME_BRICKBREAKER
        ).sumOf { PrefsManager.getStatPlays(this, it) }
        findViewById<TextView>(R.id.tvTotalMissions).text = "$totalGames TOTAL GAMES"

        contentContainer = findViewById(R.id.contentContainer)

        buildTabBar()
        renderTab("ALL")
    }

    private fun buildTabBar() {
        val accentBlue = Color.parseColor("#4f8ef7")
        val mutedColor = Color.parseColor("#666688")
        val tabs = listOf("ALL", "SNAKE", "PONG", "ASTEROIDS", "BRICK BREAKER")
        val allTabViews = mutableListOf<TextView>()

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 3 copies — seamless infinite scroll via repositioning at the edges
        repeat(3) {
            tabs.forEach { tab ->
                val tv = TextView(this).apply {
                    text = tab
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setPadding(28, 10, 28, 10)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setTextColor(if (tab == activeTab) accentBlue else mutedColor)
                    isClickable = true
                    isFocusable = true
                    tag = tab
                }
                tv.setOnClickListener {
                    activeTab = tab
                    allTabViews.forEach { v ->
                        v.setTextColor(if (v.tag == tab) accentBlue else mutedColor)
                    }
                    rebuildContent()
                }
                tabBar.addView(tv)
                allTabViews.add(tv)
            }
        }

        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        tabScroll.addView(tabBar)

        tabScroll.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                tabScroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val setWidth = tabBar.width / 3
                if (setWidth > 0) tabScroll.scrollTo(setWidth, 0)
            }
        })

        val mainHandler = Handler(Looper.getMainLooper())
        var isRepositioning = false
        tabScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            if (isRepositioning) return@setOnScrollChangeListener
            val setWidth = tabBar.width / 3
            if (setWidth <= 0) return@setOnScrollChangeListener
            val maxScrollX = tabBar.width - tabScroll.width
            val newX = when {
                scrollX < setWidth / 2              -> scrollX + setWidth
                scrollX > maxScrollX - setWidth / 2 -> scrollX - setWidth
                else                                -> return@setOnScrollChangeListener
            }
            isRepositioning = true
            tabScroll.scrollTo(newX, 0)
            mainHandler.post { isRepositioning = false }
        }

        val divider = android.view.View(this).apply {
            setBackgroundColor(Color.parseColor("#AA44FF"))
            alpha = 0.3f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 2, 0, 10) }
        }

        contentContainer.addView(tabScroll)
        contentContainer.addView(divider)
    }

    private fun rebuildContent() {
        while (contentContainer.childCount > 2) {
            contentContainer.removeViewAt(2)
        }
        renderTab(activeTab)
    }

    private fun renderTab(tab: String) {
        when (tab) {
            "ALL"           -> renderAll()
            "SNAKE"         -> renderSnake()
            "PONG"          -> renderPong()
            "ASTEROIDS"     -> renderAsteroids()
            "BRICK BREAKER" -> renderBrickBreaker()
        }
    }

    private fun renderAll() {
        val totalGames = listOf(
            PrefsManager.GAME_SNAKE, PrefsManager.GAME_PONG,
            PrefsManager.GAME_ASTEROIDS, PrefsManager.GAME_BRICKBREAKER
        ).sumOf { PrefsManager.getStatPlays(this, it) }

        addSection(contentContainer, "OVERALL")
        addMostPlayed(contentContainer)
        addRow(contentContainer, "TOTAL GAMES", fmtNum(totalGames))
        val totalMs = listOf(
            PrefsManager.GAME_SNAKE, PrefsManager.GAME_PONG,
            PrefsManager.GAME_ASTEROIDS, PrefsManager.GAME_BRICKBREAKER
        ).sumOf { PrefsManager.getStatTotalTimeMs(this, it) }
        addRow(contentContainer, "TIME IN FIELD", formatDuration(totalMs))
        addSpacer(contentContainer)

        renderSnake()
        renderPong()
        renderAsteroids()
        renderBrickBreaker()
    }

    private fun renderSnake() {
        buildGameSection(contentContainer,
            game      = PrefsManager.GAME_SNAKE,
            label     = "SNAKE",
            accentHex = "#4f8ef7",
            modes     = emptyList()
        )
        val fruits = PrefsManager.getSnakeFruits(this)
        val snakePlays = PrefsManager.getStatPlays(this, PrefsManager.GAME_SNAKE)
        val ratio = if (snakePlays > 0) "%.1f".format(fruits.toFloat() / snakePlays) else "-"
        addRow(contentContainer, "FRUITS CONSUMED", fmtNum(fruits), "#4f8ef7")
        addRow(contentContainer, "FRUITS/GAME",     ratio,          "#4f8ef7")
        addSpacer(contentContainer)
        buildLeaderboard(contentContainer, PrefsManager.GAME_SNAKE, "#4f8ef7")
        addSpacer(contentContainer)
    }

    private fun renderPong() {
        val pongFmt: (Int) -> Pair<String, Int> = { enc ->
            if (enc >= 10) {
                val ps = enc / 10; val ai = enc % 10
                Pair("$ps-$ai", if (ps >= 7) Color.parseColor("#2ecc71") else Color.parseColor("#e74c3c"))
            } else {
                Pair(fmtNum(enc), Color.parseColor("#ef5350"))
            }
        }
        buildGameSection(contentContainer,
            game           = PrefsManager.GAME_PONG,
            label          = "PONG",
            accentHex      = "#ef5350",
            modes          = listOf("easy", "medium", "hard"),
            scoreFormatter = pongFmt
        )
        addSpacer(contentContainer)
        buildLeaderboard(contentContainer, PrefsManager.GAME_PONG, "#ef5350", scoreFormatter = pongFmt)
        addSpacer(contentContainer)
    }

    private fun renderAsteroids() {
        buildGameSection(contentContainer,
            game      = PrefsManager.GAME_ASTEROIDS,
            label     = "ASTEROIDS",
            accentHex = "#00d4ff",
            modes     = emptyList()
        )
        addSpacer(contentContainer)
        buildLeaderboard(contentContainer, PrefsManager.GAME_ASTEROIDS, "#00d4ff")
        addSpacer(contentContainer)
    }

    private fun renderBrickBreaker() {
        buildGameSection(contentContainer,
            game      = PrefsManager.GAME_BRICKBREAKER,
            label     = "BRICK BREAKER",
            accentHex = "#f1c40f",
            modes     = listOf("easy", "medium", "hard")
        )
        addSpacer(contentContainer)
        buildLeaderboard(contentContainer, PrefsManager.GAME_BRICKBREAKER, "#f1c40f")
        addSpacer(contentContainer)
    }

    // ── Section builders ──────────────────────────────────────────────────────────

    private fun buildGameSection(
        container: LinearLayout, game: String, label: String,
        accentHex: String, modes: List<String>,
        scoreFormatter: ((Int) -> Pair<String, Int>)? = null
    ) {
        addSection(container, label, accentHex)

        val plays   = PrefsManager.getStatPlays(this, game)
        val total   = PrefsManager.getStatTotalScore(this, game)
        val timeMs  = PrefsManager.getStatTotalTimeMs(this, game)
        val hi      = PrefsManager.getHighScore(this, game)
        val avg     = if (plays > 0) total / plays else 0L
        val avgTime = if (plays > 0) timeMs / plays else 0L

        addRow(container, "GAMES LOGGED", fmtNum(plays), accentHex)
        addRow(container, "TOP SCORE",    fmtNum(hi),    accentHex)
        addRow(container, "AVG SCORE",    fmtNum(avg),   accentHex)
        addRow(container, "TOTAL TIME",   formatDuration(timeMs))
        addRow(container, "AVG SESSION",  formatDuration(avgTime))

        if (modes.isNotEmpty()) {
            addDivider(container, "#333344")
            val mostPlayedMode = modes.maxByOrNull { PrefsManager.getStatPlays(this, game, it) }
            modes.forEach { mode ->
                val mp = PrefsManager.getStatPlays(this, game, mode)
                val mt = PrefsManager.getStatTotalScore(this, game, mode)
                val ma = if (mp > 0) mt / mp else 0L
                val star = if (mode == mostPlayedMode && mp > 0) " *" else ""
                addSubHeader(container, mode.uppercase() + star)
                addRow(container, "GAMES",     fmtNum(mp), accentHex)
                addRow(container, "AVG SCORE", fmtNum(ma), accentHex)
                addRow(container, "TIME",      formatDuration(PrefsManager.getStatTotalTimeMs(this, game, mode)))
                buildLeaderboard(container, "${game}_${mode}", accentHex, showHeader = false, scoreFormatter = scoreFormatter)
            }
        }
    }

    private fun buildLeaderboard(
        container: LinearLayout,
        game: String,
        accentHex: String,
        header: String = "TOP SCORES",
        showHeader: Boolean = true,
        scoreFormatter: ((Int) -> Pair<String, Int>)? = null
    ) {
        val entries = LeaderboardManager.getEntries(this, game)
        if (entries.isEmpty()) return
        if (showHeader) addSubHeader(container, header)
        addLeaderboardHeader(container, accentHex)
        entries.forEachIndexed { i, e ->
            val (scoreText, scoreColor) = scoreFormatter?.invoke(e.score)
                ?: Pair(fmtNum(e.score), Color.parseColor(accentHex))
            addLeaderboardRow(container, "#${i + 1}", e.initials.trim(), scoreText, e.formattedDate, accentHex, scoreColor)
        }
    }

    // ── Most played ───────────────────────────────────────────────────────────────

    private fun addMostPlayed(container: LinearLayout) {
        data class Candidate(val label: String, val plays: Int, val accent: String)

        val candidates = listOf(
            Candidate("SNAKE",         PrefsManager.getStatPlays(this, PrefsManager.GAME_SNAKE),        "#4f8ef7"),
            Candidate("PONG",          PrefsManager.getStatPlays(this, PrefsManager.GAME_PONG),         "#ef5350"),
            Candidate("ASTEROIDS",     PrefsManager.getStatPlays(this, PrefsManager.GAME_ASTEROIDS),    "#00d4ff"),
            Candidate("BRICK BREAKER", PrefsManager.getStatPlays(this, PrefsManager.GAME_BRICKBREAKER), "#f1c40f")
        )
        val top = candidates.maxByOrNull { it.plays }
        if (top != null && top.plays > 0) {
            addRow(container, "MOST PLAYED", top.label, top.accent)
        }

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
            text = "===  $title  ==="
            setTextColor(Color.parseColor(accentHex))
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 10)
        }
        container.addView(tv)
        addDivider(container, accentHex)
    }

    private fun addSubHeader(container: LinearLayout, title: String) {
        val tv = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#AA44FF"))
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 6)
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
            setPadding(0, 6, 0, 6)
        }
        val tvLabel = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#666688"))
            textSize = 22f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 16, 0)
        }
        val tvValue = TextView(this).apply {
            text = value
            setTextColor(Color.parseColor(valueColorHex))
            textSize = 22f
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16, 0, 0, 0)
        }
        row.addView(tvLabel)
        row.addView(tvValue)
        container.addView(row)
    }

    private fun addLeaderboardHeader(container: LinearLayout, accentHex: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2, 0, 4)
        }
        fun col(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor(accentHex))
            textSize = 16f
            gravity = Gravity.CENTER
            alpha = 0.55f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(col("RANK"))
        row.addView(col("NAME"))
        row.addView(col("SCORE"))
        row.addView(col("DATE"))
        container.addView(row)
        addDivider(container, accentHex)
    }

    private fun addLeaderboardRow(
        container: LinearLayout,
        rank: String, initials: String, score: String, date: String,
        accentHex: String, scoreColor: Int = -1
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }
        fun col(text: String, color: String) = TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor(color))
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun colInt(text: String, color: Int) = TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sc = if (scoreColor == -1) Color.parseColor(accentHex) else scoreColor
        row.addView(col(rank,                     accentHex))
        row.addView(col(initials.ifEmpty { "-" }, "#FFFFFF"))
        row.addView(colInt(score,                 sc))
        row.addView(col(date,                     "#444466"))
        container.addView(row)
    }

    private fun addDivider(container: LinearLayout, colorHex: String) {
        val v = android.view.View(this).apply {
            setBackgroundColor(Color.parseColor(colorHex))
            alpha = 0.3f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 6, 0, 6) }
        }
        container.addView(v)
    }

    private fun addSpacer(container: LinearLayout) {
        val v = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24)
        }
        container.addView(v)
    }

    // ── Formatting ────────────────────────────────────────────────────────────────

    private fun fmtNum(n: Long): String = "%,d".format(n)
    private fun fmtNum(n: Int): String = "%,d".format(n)

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
