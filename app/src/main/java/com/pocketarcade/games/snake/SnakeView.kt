package com.pocketarcade.games.snake

import androidx.core.content.res.ResourcesCompat
import com.pocketarcade.R
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.pocketarcade.GameTheme
import com.pocketarcade.SoundManager
import com.pocketarcade.snakeBodyBorder
import com.pocketarcade.snakeBodyEven
import com.pocketarcade.snakeBodyOdd
import android.view.HapticFeedbackConstants
import kotlin.math.abs
import kotlin.math.min

class SnakeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val COLS = 20
    private val ROWS = 20
    private val TICK_MS = 120L

    private var snake = mutableListOf(Point(10, 10), Point(9, 10), Point(8, 10))
    private var dir = Point(1, 0)
    private var nextDir = Point(1, 0)
    private var food = Point(4, 4)
    private var score = 0
    private var gameOver = false
    private var started = false
    private var demoMode = false

    var onScoreChanged: ((Int) -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null
    var onGameStarted: (() -> Unit)? = null
    var onDemoStopped: (() -> Unit)? = null
    var onFruitEaten: (() -> Unit)? = null

    var swipeThresholdDp: Float = 20f
    private var swipeEnabled = false

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            if (!gameOver) handler.postDelayed(this, TICK_MS)
        }
    }

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var extTouchStartX = 0f
    private var extTouchStartY = 0f

    // Ã¢â€â‚¬Ã¢â€â‚¬ Paints (Classic dark defaults) Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    private val bgPaint        = Paint().apply { color = Color.parseColor("#0a0d14") }
    private val gridPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1e3d6e") }
    private val borderPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563eb")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val foodPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#f87171") }
    private val snakeHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#60a5fa") }
    private val snakeEvenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563eb") }
    private val snakeOddPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1d4ed8") }
    private val segBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3b82f6")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val overlayPaint      = Paint().apply { color = Color.parseColor("#CC0a0d14") }
    private val gameOverTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#f87171")
        typeface = (ResourcesCompat.getFont(context, R.font.press_start_2p) ?: Typeface.MONOSPACE)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = (ResourcesCompat.getFont(context, R.font.press_start_2p) ?: Typeface.MONOSPACE)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94a3b8")
        typeface = (ResourcesCompat.getFont(context, R.font.press_start_2p) ?: Typeface.MONOSPACE)
        textAlign = Paint.Align.CENTER
    }
    private val demoTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#fbbf24")
        typeface = (ResourcesCompat.getFont(context, R.font.press_start_2p) ?: Typeface.MONOSPACE)
        textAlign = Paint.Align.CENTER
        alpha = 200
    }

    private var cellSize = 0
    private val segRect = RectF()

    // Ã¢â€â‚¬Ã¢â€â‚¬ Theme Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    fun applyTheme(t: GameTheme) {
        bgPaint.color        = t.bg
        gridPaint.color      = t.gridDot
        borderPaint.color    = t.accent
        snakeHeadPaint.color = t.player
        snakeEvenPaint.color = t.snakeBodyEven
        snakeOddPaint.color  = t.snakeBodyOdd
        segBorderPaint.color = t.snakeBodyBorder
        foodPaint.color      = t.collect
        overlayPaint.color   = t.overlay
        gameOverTextPaint.color = t.rival
        scorePaint.color     = t.text
        hintPaint.color      = t.muted
        post { setBackgroundColor(t.bg) }
        invalidate()
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ Public API Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    fun startGame() {
        demoMode = false
        swipeEnabled = true
        resetState()
        started = true
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, TICK_MS)
        onGameStarted?.invoke()
        invalidate()
    }

    fun startDemo() {
        demoMode = true
        swipeEnabled = false
        resetState()
        started = true
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, TICK_MS)
        invalidate()
    }

    fun stopGame() {
        handler.removeCallbacks(tickRunnable)
        swipeEnabled = false
        gameOver = true
        started = false
    }

    fun isRunning() = started && !gameOver
    fun isUserPlaying() = started && !gameOver && !demoMode

    // Ã¢â€â‚¬Ã¢â€â‚¬ Game logic Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    private fun resetState() {
        snake = mutableListOf(Point(10, 10), Point(9, 10), Point(8, 10))
        dir = Point(1, 0); nextDir = Point(1, 0)
        food = randomFood()
        score = 0; gameOver = false
        onScoreChanged?.invoke(0)
    }

    private fun randomFood(): Point {
        var p: Point
        do { p = Point((0 until COLS).random(), (0 until ROWS).random()) }
        while (snake.any { it.x == p.x && it.y == p.y })
        return p
    }

    private fun tick() {
        if (gameOver) return
        if (demoMode) nextDir = aiDir()
        dir = nextDir
        val head = Point(snake[0].x + dir.x, snake[0].y + dir.y)
        if (head.x < 0 || head.x >= COLS || head.y < 0 || head.y >= ROWS ||
            snake.any { it.x == head.x && it.y == head.y }
        ) {
            swipeEnabled = false
            gameOver = true
            if (demoMode) {
                invalidate()
                handler.postDelayed({ if (demoMode) startDemo() }, 1800)
            } else {
                SoundManager.play(SoundManager.SFX.SNAKE_DIE, context)
                onGameOver?.invoke(score)
            }
            invalidate()
            return
        }
        val ate = head.x == food.x && head.y == food.y
        snake.add(0, head)
        if (!ate) snake.removeAt(snake.size - 1)
        else {
            score++; food = randomFood(); onScoreChanged?.invoke(score)
            if (!demoMode) { onFruitEaten?.invoke()
                SoundManager.play(SoundManager.SFX.SNAKE_EAT, context)
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
        invalidate()
    }

    private fun aiDir(): Point {
        val head = snake[0]
        val dirs = listOf(Point(0, -1), Point(0, 1), Point(-1, 0), Point(1, 0))
        val possible = dirs.filter { d -> d.x != -dir.x || d.y != -dir.y }
        val safe = possible.filter { d ->
            val nx = head.x + d.x; val ny = head.y + d.y
            nx in 0 until COLS && ny in 0 until ROWS &&
                snake.dropLast(1).none { it.x == nx && it.y == ny }
        }
        if (safe.isEmpty()) return dir
        return safe.minBy { d ->
            abs(head.x + d.x - food.x) + abs(head.y + d.y - food.y) + Math.random() * 0.4
        }
    }

    fun steer(dx: Int, dy: Int) {
        if (!swipeEnabled || !started || gameOver || demoMode) return
        val nd = Point(dx, dy)
        if (nd.x != -dir.x || nd.y != -dir.y) nextDir = nd
    }

    fun dpadUp()    = steer(0, -1)
    fun dpadDown()  = steer(0, 1)
    fun dpadLeft()  = steer(-1, 0)
    fun dpadRight() = steer(1, 0)

    // Ã¢â€â‚¬Ã¢â€â‚¬ Touch (game canvas) Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val threshPx = swipeThresholdDp * resources.displayMetrics.density
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x; touchStartY = event.y
                if (demoMode) { demoMode = false; stopGame(); onDemoStopped?.invoke() }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeEnabled) return true
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                if (abs(dx) < threshPx && abs(dy) < threshPx) return true
                if (abs(dx) > abs(dy)) steer(if (dx > 0) 1 else -1, 0)
                else steer(0, if (dy > 0) 1 else -1)
                touchStartX = event.x; touchStartY = event.y
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Touch forwarded from the swipe zone panel
    fun handleExternalTouch(event: MotionEvent): Boolean {
        val threshPx = swipeThresholdDp * resources.displayMetrics.density
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                extTouchStartX = event.x; extTouchStartY = event.y
                if (demoMode) { demoMode = false; stopGame(); onDemoStopped?.invoke() }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeEnabled) return true
                val dx = event.x - extTouchStartX
                val dy = event.y - extTouchStartY
                if (abs(dx) < threshPx && abs(dy) < threshPx) return true
                if (abs(dx) > abs(dy)) steer(if (dx > 0) 1 else -1, 0)
                else steer(0, if (dy > 0) 1 else -1)
                extTouchStartX = event.x; extTouchStartY = event.y
                return true
            }
        }
        return false
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ Measure Ã¢â‚¬â€ always square Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = min(w / COLS, h / ROWS)
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ Draw Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    override fun onDraw(canvas: Canvas) {
        if (cellSize == 0) return
        val cs = cellSize.toFloat()
        val gridW = COLS * cs
        val gridH = ROWS * cs
        val offsetX = (width - gridW) / 2f
        val offsetY = 0f

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        canvas.save()
        canvas.translate(offsetX, offsetY)

        // Border around the play area
        canvas.drawRect(0f, 0f, gridW, gridH, borderPaint)

        // Grid dots
        val dotR = cs * 0.09f
        for (x in 0 until COLS) for (y in 0 until ROWS) {
            canvas.drawCircle(x * cs + cs / 2f, y * cs + cs / 2f, dotR, gridPaint)
        }

        // Food
        val fx = food.x * cs + cs / 2f
        val fy = food.y * cs + cs / 2f
        canvas.drawCircle(fx, fy, cs / 2f - 2f, foodPaint)

        // Snake Ã¢â‚¬â€ rounded rect segments + border ring
        val r = cs / 3.5f
        snake.forEachIndexed { i, seg ->
            val fill = when {
                i == 0 -> snakeHeadPaint
                i % 2 == 0 -> snakeEvenPaint
                else -> snakeOddPaint
            }
            segRect.set(
                seg.x * cs + 1.5f, seg.y * cs + 1.5f,
                seg.x * cs + cs - 1.5f, seg.y * cs + cs - 1.5f
            )
            canvas.drawRoundRect(segRect, r, r, fill)
            if (i > 0) canvas.drawRoundRect(segRect, r, r, segBorderPaint)
        }

        // Overlays
        if (!started) {
            canvas.drawRect(0f, 0f, gridW, gridH, overlayPaint)
            val cx = gridW / 2f; val cy = gridH / 2f
            gameOverTextPaint.textSize = cs * 1.1f
            canvas.drawText("PRESS START", cx, cy - cs * 0.5f, gameOverTextPaint)
            hintPaint.textSize = cs * 0.6f
            canvas.drawText("swipe or use D-pad", cx, cy + cs * 0.8f, hintPaint)
        } else if (gameOver && !demoMode) {
            canvas.drawRect(0f, 0f, gridW, gridH, overlayPaint)
            val cx = gridW / 2f; val cy = gridH / 2f
            gameOverTextPaint.textSize = cs * 1.1f
            canvas.drawText("GAME OVER", cx, cy - cs * 0.8f, gameOverTextPaint)
            scorePaint.textSize = cs * 2.2f
            canvas.drawText("$score", cx, cy + cs * 0.6f, scorePaint)
        }

        if (demoMode && started) {
            demoTagPaint.textSize = cs * 0.7f
            canvas.drawText("Ã¢â€“Â¶ DEMO", gridW / 2f, gridH - cs * 0.4f, demoTagPaint)
        }

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}
