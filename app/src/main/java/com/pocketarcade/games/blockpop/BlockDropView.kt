package com.pocketarcade.games.blockpop

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.pocketarcade.GameTheme
import com.pocketarcade.withAlpha
import kotlin.math.min

// ── Difficulty ────────────────────────────────────────────────────────────

enum class BlockDropDifficulty(val cols: Int, val rows: Int, val key: String) {
    EASY  (cols =  8, rows = 10, key = "easy"),
    MEDIUM(cols = 10, rows = 12, key = "medium"),
    HARD  (cols = 12, rows = 14, key = "hard"),
}

// ── Constants ─────────────────────────────────────────────────────────────

private const val MIN_GROUP        = 2
private const val SCORE_MULTIPLIER = 10
private const val CLEAR_BONUS      = 1000   // bonus added when a level is fully cleared
private const val POP_ANIM_MS      = 260L

/** Colors available per level (1-indexed). Caps at 5 after level 5. */
private fun levelNumColors(level: Int): Int = when {
    level <= 2 -> 3
    level <= 4 -> 4
    else       -> 5
}

// Default block colors (replaced per-theme in applyTheme)
private val DEFAULT_BLOCK_COLORS = intArrayOf(
    Color.parseColor("#E53935"),
    Color.parseColor("#43A047"),
    Color.parseColor("#1E88E5"),
    Color.parseColor("#FDD835"),
    Color.parseColor("#8E24AA"),
)

private const val EMPTY = -1

enum class BlockDropState { PLAYING, POPPING, STUCK, CLEARED }

// ── View ──────────────────────────────────────────────────────────────────

class BlockDropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // ── Difficulty ─────────────────────────────────────────────────────────

    var difficulty: BlockDropDifficulty = BlockDropDifficulty.MEDIUM
        set(value) {
            field = value
            recalcBoardMetrics()
            startLevel()
        }

    // Convenience shorthand that changes when difficulty does
    private val cols get() = difficulty.cols
    private val rows get() = difficulty.rows

    // ── Game state ─────────────────────────────────────────────────────────

    private var board       = Array(rows) { IntArray(cols) { EMPTY } }
    private var levelIndex  = 0   // 0-based; displayed as levelIndex+1
    private var score       = 0   // current level score
    private var totalScore  = 0   // accumulated score from cleared levels
    private var totalMoves  = 0   // taps across the whole game session
    private var bestScore   = 0
    private var gameState   = BlockDropState.PLAYING

    // Block color palette — updated from theme in applyTheme()
    private var blockColors = DEFAULT_BLOCK_COLORS.copyOf()

    private val highlightedCells = mutableSetOf<Int>()
    private val poppingCells     = mutableSetOf<Int>()
    private var lastPointsLabel: String? = null
    private var lastPointsAlpha = 0f

    // ── Callbacks ──────────────────────────────────────────────────────────

    /** Fired when a new game/level starts. */
    var onGameStarted: (() -> Unit)? = null
    /** Fired when stuck — parameter is total accumulated score (all levels + partial). */
    var onGameOver: ((score: Int) -> Unit)? = null

    fun loadBestScore(b: Int) { bestScore = b }

    // ── Layout ─────────────────────────────────────────────────────────────

    private var cellSize    = 0f
    private var boardLeft   = 0f
    private var boardTop    = 0f
    private var boardRight  = 0f
    private var boardBottom = 0f

    // ── Theme ──────────────────────────────────────────────────────────────

    private var themeAccent = Color.parseColor("#FF6B35")

    fun applyTheme(theme: GameTheme) {
        themeAccent = theme.player
        hudAccentPaint.color = themeAccent
        plusPaint.color      = themeAccent
        blockColors = generateBlockColors(theme.player)
        invalidate()
    }

    /** Generate 5 evenly hue-spaced colors rooted at the theme's player color. */
    private fun generateBlockColors(baseColor: Int): IntArray {
        val hsv = FloatArray(3)
        Color.colorToHSV(baseColor, hsv)
        return IntArray(5) { i ->
            Color.HSVToColor(floatArrayOf(
                (hsv[0] + i * 72f) % 360f,
                hsv[1].coerceAtLeast(0.65f),
                hsv[2].coerceAtLeast(0.72f)
            ))
        }
    }

    // ── Paints ─────────────────────────────────────────────────────────────

    private val blockPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val emptyPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D0D1A")
    }
    private val bgPaint       = Paint().apply { color = Color.parseColor("#0A0A0F") }
    private val boardBgPaint  = Paint().apply { color = Color.parseColor("#05050F") }
    private val shinePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
    }
    private val scanlinePaint = Paint().apply { color = Color.parseColor("#14000000") }

    private val hudLabelPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#445566"); typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val hudValuePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }
    private val hudAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B35"); typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B35"); typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    private val overlayBgPaint    = Paint().apply { color = Color.parseColor("#DD000000") }
    private val overlayTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val overlayBodyPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA"); typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    // ── Handler ────────────────────────────────────────────────────────────

    private val handler = Handler(Looper.getMainLooper())
    private val floatLabelRunnable = object : Runnable {
        override fun run() {
            lastPointsAlpha -= 0.06f
            if (lastPointsAlpha > 0f) { invalidate(); handler.postDelayed(this, 16) }
            else { lastPointsLabel = null; invalidate() }
        }
    }
    private val overlayBtnRect = RectF()

    // ── Lifecycle ──────────────────────────────────────────────────────────

    init { startLevel() }

    fun onPause()   { handler.removeCallbacksAndMessages(null) }
    fun onResume()  { invalidate() }
    fun onDestroy() { handler.removeCallbacksAndMessages(null) }

    // ── Measure / layout ───────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcBoardMetrics()
        setTextSizes(h)
    }

    private fun recalcBoardMetrics() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val hudH    = h * 0.13f
        val padding = w * 0.02f
        val availW  = w - padding * 2
        val availH  = h - hudH - padding * 2
        cellSize    = min(availW / cols, availH / rows)
        val boardW  = cellSize * cols
        val boardH  = cellSize * rows
        boardLeft   = (w - boardW) / 2f
        boardTop    = hudH + (availH - boardH) / 2f + padding
        boardRight  = boardLeft + boardW
        boardBottom = boardTop + boardH
    }

    private fun setTextSizes(h: Int) {
        val hf = h.toFloat()
        hudLabelPaint.textSize  = hf * 0.022f
        hudValuePaint.textSize  = hf * 0.030f
        hudAccentPaint.textSize = hf * 0.030f
        plusPaint.textSize      = hf * 0.028f
        overlayTitlePaint.textSize = hf * 0.055f
        overlayBodyPaint.textSize  = hf * 0.028f
        btnTextPaint.textSize      = hf * 0.030f
    }

    // ── Draw ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(boardLeft, boardTop, boardRight, boardBottom, boardBgPaint)
        for (r in 0 until rows) for (c in 0 until cols) drawCell(canvas, r, c)
        drawScanlines(canvas, w, h)
        drawHud(canvas, w, h)
        drawFloatingLabel(canvas, w)
        when (gameState) {
            BlockDropState.STUCK -> {
                val finalScore = totalScore + score
                val body = buildString {
                    append("Level ${levelIndex + 1}  •  Score: %,d".format(finalScore))
                    if (bestScore > 0) append("\nBest: %,d".format(bestScore))
                }
                drawOverlay(canvas, w, h, "STUCK!", Color.parseColor("#FF4444"),
                    body, "TRY AGAIN", Color.parseColor("#FF4444"))
            }
            BlockDropState.CLEARED -> {
                val nextColors = levelNumColors(levelIndex + 2)
                val body = "Level ${levelIndex + 1} cleared!  +%,d".format(CLEAR_BONUS) +
                    "\nLevel ${levelIndex + 2}  •  $nextColors colors"
                drawOverlay(canvas, w, h, "CLEARED!", Color.parseColor("#44FF88"),
                    body, "NEXT LEVEL", Color.parseColor("#55AAFF"))
            }
            else -> {}
        }
    }

    private fun drawCell(canvas: Canvas, r: Int, c: Int) {
        val left = boardLeft + c * cellSize
        val top  = boardTop  + r * cellSize
        val pad  = cellSize * 0.07f
        val rect = RectF(left + pad, top + pad, left + cellSize - pad, top + cellSize - pad)
        val rad  = cellSize * 0.12f
        val key  = encode(r, c)
        val colorIdx = board[r][c]
        if (colorIdx == EMPTY) { canvas.drawRoundRect(rect, rad, rad, emptyPaint); return }
        val base        = blockColors[colorIdx]
        val isHighlight = key in highlightedCells
        val isPopping   = key in poppingCells
        blockPaint.color = when {
            isPopping   -> Color.WHITE
            isHighlight -> base
            else        -> darken(base, 0.75f)
        }
        canvas.drawRoundRect(rect, rad, rad, blockPaint)
        if (isHighlight && !isPopping) {
            highlightPaint.color = base
            canvas.drawRoundRect(rect, rad, rad, highlightPaint)
        }
        if (!isPopping) {
            canvas.drawCircle(left + cellSize * 0.28f, top + cellSize * 0.28f,
                cellSize * 0.08f, shinePaint)
        }
    }

    private fun drawScanlines(canvas: Canvas, w: Float, h: Float) {
        var y = 0f; while (y < h) { canvas.drawRect(0f, y, w, y + 2f, scanlinePaint); y += 4f }
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val hudCy      = h * 0.065f
        val third      = w / 3f
        val displayLvl = levelIndex + 1
        canvas.drawText("LEVEL",               third * 0.5f, hudCy - h * 0.025f, hudLabelPaint)
        canvas.drawText("SCORE",               third * 1.5f, hudCy - h * 0.025f, hudLabelPaint)
        canvas.drawText("MOVES",               third * 2.5f, hudCy - h * 0.025f, hudLabelPaint)
        canvas.drawText("$displayLvl",         third * 0.5f, hudCy + h * 0.020f, hudAccentPaint)
        canvas.drawText("${totalScore + score}",third * 1.5f, hudCy + h * 0.020f, hudValuePaint)
        canvas.drawText("$totalMoves",         third * 2.5f, hudCy + h * 0.020f, hudValuePaint)
        hudLabelPaint.textSize = height * 0.018f
        canvas.drawText("${levelNumColors(displayLvl)} colors · ${difficulty.cols}×${difficulty.rows}",
            third * 0.5f, hudCy + h * 0.042f, hudLabelPaint)
        hudLabelPaint.textSize = height * 0.022f
    }

    private fun drawFloatingLabel(canvas: Canvas, w: Float) {
        val label = lastPointsLabel ?: return
        plusPaint.alpha = (lastPointsAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawText(label, w / 2f, boardTop - height * 0.01f, plusPaint)
    }

    private fun drawOverlay(canvas: Canvas, w: Float, h: Float,
                            title: String, titleColor: Int,
                            body: String, btnLabel: String, btnColor: Int) {
        canvas.drawRect(0f, 0f, w, h, overlayBgPaint)
        val cx = w / 2f; val cy = h / 2f
        overlayTitlePaint.color = titleColor
        canvas.drawText(title, cx, cy - h * 0.12f, overlayTitlePaint)
        body.split("\n").forEachIndexed { i, line ->
            canvas.drawText(line, cx, cy - h * 0.04f + i * h * 0.04f, overlayBodyPaint)
        }
        val btnW = w * 0.5f; val btnH = h * 0.065f; val btnTop = cy + h * 0.10f
        overlayBtnRect.set(cx - btnW / 2f, btnTop, cx + btnW / 2f, btnTop + btnH)
        btnPaint.color = btnColor
        canvas.drawRoundRect(overlayBtnRect, 8f, 8f, btnPaint)
        btnTextPaint.color = btnColor
        canvas.drawText(btnLabel, cx, overlayBtnRect.centerY() + btnTextPaint.textSize * 0.35f, btnTextPaint)
    }

    // ── Touch ──────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val x = event.x; val y = event.y
        if (gameState != BlockDropState.PLAYING && gameState != BlockDropState.POPPING) {
            if (overlayBtnRect.contains(x, y)) onOverlayButtonTapped()
            return true
        }
        if (gameState == BlockDropState.POPPING) return true
        val col = ((x - boardLeft) / cellSize).toInt()
        val row = ((y - boardTop)  / cellSize).toInt()
        if (row !in 0 until rows || col !in 0 until cols) return true
        val group = findGroup(row, col)
        if (group.size < MIN_GROUP) {
            highlightedCells.clear()
            highlightedCells.addAll(group.map { encode(it.first, it.second) })
            invalidate()
            handler.postDelayed({ highlightedCells.clear(); invalidate() }, 250)
            return true
        }
        clearGroup(group)
        return true
    }

    // ── Game logic ─────────────────────────────────────────────────────────

    /** Reset and start a fresh game from level 1. */
    fun resetForNewGame() {
        levelIndex = 0; totalScore = 0; totalMoves = 0
        startLevel()
    }

    /** Restart mid-game — resets to level 1; no score is posted for the interrupted game. */
    fun restart() = resetForNewGame()

    private fun startLevel() {
        if (levelIndex == 0) post { onGameStarted?.invoke() }
        val numColors = levelNumColors(levelIndex + 1)
        board     = Array(rows) { IntArray(cols) { (0 until numColors).random() } }
        score     = 0
        gameState = BlockDropState.PLAYING
        highlightedCells.clear(); poppingCells.clear(); lastPointsLabel = null
        invalidate()
    }

    private fun clearGroup(group: List<Pair<Int, Int>>) {
        val pts = group.size * group.size * SCORE_MULTIPLIER
        gameState = BlockDropState.POPPING
        highlightedCells.clear()
        poppingCells.clear()
        poppingCells.addAll(group.map { encode(it.first, it.second) })
        invalidate()

        handler.postDelayed({
            for ((r, c) in group) board[r][c] = EMPTY
            applyGravity()
            collapseColumns()
            poppingCells.clear()
            score      += pts
            totalMoves += 1
            var labelPts = pts

            when {
                isBoardClear() -> {
                    score    += CLEAR_BONUS
                    labelPts += CLEAR_BONUS
                    gameState = BlockDropState.CLEARED
                    // bestScore updated in STUCK path — don't award until game ends
                }
                isStuck() -> {
                    gameState = BlockDropState.STUCK
                    val finalScore = totalScore + score
                    if (bestScore == 0 || finalScore > bestScore) bestScore = finalScore
                    post { onGameOver?.invoke(finalScore) }
                }
                else -> gameState = BlockDropState.PLAYING
            }

            lastPointsLabel = "+$labelPts"
            lastPointsAlpha = 1f
            handler.removeCallbacks(floatLabelRunnable)
            handler.post(floatLabelRunnable)
            invalidate()
        }, POP_ANIM_MS)
    }

    private fun findGroup(row: Int, col: Int): List<Pair<Int, Int>> {
        val color = board[row][col]
        if (color == EMPTY) return emptyList()
        val visited = mutableSetOf<Int>()
        val result  = mutableListOf<Pair<Int, Int>>()
        val stack   = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(row to col)
        while (stack.isNotEmpty()) {
            val (r, c) = stack.removeLast()
            val key = encode(r, c)
            if (key in visited) continue
            visited += key; result += (r to c)
            for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val nr = r + dr; val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols
                    && board[nr][nc] == color && encode(nr, nc) !in visited)
                    stack.addLast(nr to nc)
            }
        }
        return result
    }

    private fun applyGravity() {
        for (c in 0 until cols) {
            var write = rows - 1
            for (r in rows - 1 downTo 0) {
                if (board[r][c] != EMPTY) {
                    board[write][c] = board[r][c]
                    if (write != r) board[r][c] = EMPTY
                    write--
                }
            }
            while (write >= 0) { board[write][c] = EMPTY; write-- }
        }
    }

    private fun collapseColumns() {
        val active = mutableListOf<IntArray>()
        for (c in 0 until cols) {
            val col = IntArray(rows) { r -> board[r][c] }
            if (col.any { it != EMPTY }) active += col
        }
        for (c in 0 until cols) {
            val col = active.getOrNull(c)
            for (r in 0 until rows) board[r][c] = col?.get(r) ?: EMPTY
        }
    }

    private fun isBoardClear() = board.all { row -> row.all { it == EMPTY } }

    private fun isStuck(): Boolean {
        for (r in 0 until rows) for (c in 0 until cols)
            if (findGroup(r, c).size >= MIN_GROUP) return false
        return true
    }

    private fun onOverlayButtonTapped() {
        when (gameState) {
            BlockDropState.CLEARED -> {
                totalScore += score   // bank the cleared level's score
                levelIndex++          // advance to next level (infinite)
                startLevel()
            }
            BlockDropState.STUCK -> resetForNewGame()
            else -> {}
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun encode(r: Int, c: Int) = r * cols + c

    private fun darken(color: Int, factor: Float) = Color.rgb(
        (Color.red(color)   * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color)  * factor).toInt().coerceIn(0, 255)
    )
}
