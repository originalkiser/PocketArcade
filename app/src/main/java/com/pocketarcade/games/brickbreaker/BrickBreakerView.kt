package com.pocketarcade.games.brickbreaker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.pocketarcade.SoundManager
import com.pocketarcade.brick1
import com.pocketarcade.brick2
import com.pocketarcade.brick3
import com.pocketarcade.withAlpha
import kotlin.math.*

enum class BBState { IDLE, AIM, PLAYING, LEVEL_CLEAR, GAME_OVER, WIN }
enum class PowerUpType { WIDE_PADDLE, SLOW_BALL, MULTI_BALL, LASER }
enum class BBDifficulty { EASY, MEDIUM, HARD }

data class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float = 16f)
data class Brick(val col: Int, val row: Int, var hp: Int)
data class PowerUp(var x: Float, var y: Float, val type: PowerUpType)

class BrickBreakerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    companion object {
        const val COLS = 8
        const val BRICK_ROWS = 7
        const val PADDLE_H = 12f
        const val BASE_BALL_SPEED = 28f
        fun speedForDifficulty(d: BBDifficulty) = when (d) {
            BBDifficulty.EASY   -> 18f
            BBDifficulty.MEDIUM -> 22f
            BBDifficulty.HARD   -> 28f
        }
        const val SLOW_BALL_MULTIPLIER = 0.6f
        const val WIDE_PADDLE_FACTOR = 1.7f
        const val POWERUP_SPEED = 3f

        // 10 levels: each is a BRICK_ROWSÃƒÆ’Ã¢â‚¬â€COLS array of HP (0 = empty, 1/2/3 = HP)
        val LEVELS = arrayOf(
            // Level 1 ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“ simple row fill
            arrayOf(intArrayOf(0,0,0,0,0,0,0,0), intArrayOf(0,0,0,0,0,0,0,0),
                    intArrayOf(1,1,1,1,1,1,1,1), intArrayOf(1,1,1,1,1,1,1,1),
                    intArrayOf(0,0,0,0,0,0,0,0), intArrayOf(0,0,0,0,0,0,0,0),
                    intArrayOf(0,0,0,0,0,0,0,0)),
            // Level 2
            arrayOf(intArrayOf(1,1,1,1,1,1,1,1), intArrayOf(0,1,1,1,1,1,1,0),
                    intArrayOf(0,0,1,1,1,1,0,0), intArrayOf(0,0,0,1,1,0,0,0),
                    intArrayOf(0,0,0,0,0,0,0,0), intArrayOf(0,0,0,0,0,0,0,0),
                    intArrayOf(0,0,0,0,0,0,0,0)),
            // Level 3
            arrayOf(intArrayOf(2,2,2,2,2,2,2,2), intArrayOf(1,1,1,1,1,1,1,1),
                    intArrayOf(1,1,1,1,1,1,1,1), intArrayOf(0,0,0,0,0,0,0,0),
                    intArrayOf(0,0,0,0,0,0,0,0), intArrayOf(0,0,0,0,0,0,0,0),
                    intArrayOf(0,0,0,0,0,0,0,0)),
            // Level 4 ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“ checkerboard
            arrayOf(intArrayOf(1,0,1,0,1,0,1,0), intArrayOf(0,1,0,1,0,1,0,1),
                    intArrayOf(1,0,2,0,2,0,1,0), intArrayOf(0,1,0,2,2,0,1,0),
                    intArrayOf(1,0,1,0,1,0,1,0), intArrayOf(0,0,0,0,0,0,0,0),
                    intArrayOf(0,0,0,0,0,0,0,0)),
            // Level 5
            arrayOf(intArrayOf(3,0,0,0,0,0,0,3), intArrayOf(0,2,0,0,0,0,2,0),
                    intArrayOf(0,0,1,1,1,1,0,0), intArrayOf(0,0,1,2,2,1,0,0),
                    intArrayOf(0,0,1,1,1,1,0,0), intArrayOf(0,0,0,0,0,0,0,0),
                    intArrayOf(0,0,0,0,0,0,0,0)),
            // Level 6
            arrayOf(intArrayOf(2,2,2,2,2,2,2,2), intArrayOf(2,0,0,0,0,0,0,2),
                    intArrayOf(2,0,1,1,1,1,0,2), intArrayOf(2,0,1,0,0,1,0,2),
                    intArrayOf(2,0,1,1,1,1,0,2), intArrayOf(2,0,0,0,0,0,0,2),
                    intArrayOf(2,2,2,2,2,2,2,2)),
            // Level 7
            arrayOf(intArrayOf(3,1,3,1,3,1,3,1), intArrayOf(1,3,1,3,1,3,1,3),
                    intArrayOf(3,1,3,1,3,1,3,1), intArrayOf(1,3,1,3,1,3,1,3),
                    intArrayOf(0,0,0,0,0,0,0,0), intArrayOf(0,0,0,0,0,0,0,0),
                    intArrayOf(0,0,0,0,0,0,0,0)),
            // Level 8
            arrayOf(intArrayOf(3,3,3,3,3,3,3,3), intArrayOf(3,2,2,2,2,2,2,3),
                    intArrayOf(3,2,1,1,1,1,2,3), intArrayOf(3,2,1,0,0,1,2,3),
                    intArrayOf(3,2,1,1,1,1,2,3), intArrayOf(3,2,2,2,2,2,2,3),
                    intArrayOf(3,3,3,3,3,3,3,3)),
            // Level 9
            arrayOf(intArrayOf(1,2,3,2,3,2,3,1), intArrayOf(2,3,2,3,2,3,2,2),
                    intArrayOf(3,2,3,2,3,2,3,3), intArrayOf(2,3,2,3,2,3,2,2),
                    intArrayOf(1,2,3,2,3,2,3,1), intArrayOf(0,0,0,0,0,0,0,0),
                    intArrayOf(0,0,0,0,0,0,0,0)),
            // Level 10 ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“ fortress
            arrayOf(intArrayOf(3,3,3,3,3,3,3,3), intArrayOf(3,0,3,0,0,3,0,3),
                    intArrayOf(3,3,3,3,3,3,3,3), intArrayOf(3,0,0,3,3,0,0,3),
                    intArrayOf(3,3,3,3,3,3,3,3), intArrayOf(3,0,3,0,0,3,0,3),
                    intArrayOf(3,3,3,3,3,3,3,3))
        )
    }

    var difficulty = BBDifficulty.MEDIUM

    var onScoreChanged: ((Int) -> Unit)? = null
    var onLevelChanged: ((Int) -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null
    var onWin: ((Int) -> Unit)? = null
    var onGameStarted: (() -> Unit)? = null

    private var state = BBState.IDLE
    private var demoMode = false

    private val balls = mutableListOf<Ball>()
    private val bricks = mutableListOf<Brick>()
    private val powerUps = mutableListOf<PowerUp>()

    private var paddleX = 0f
    private var paddleW = 0f
    private var paddleWBase = 0f
    private var score = 0
    private var level = 0
    private var ballSpeed = BASE_BALL_SPEED
    private var widePaddleTimer = 0
    private var slowBallTimer = 0
    private var laserTimer = 0

    // Aim mode
    private var aimAngle = -PI.toFloat() / 2f

    // Touch ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â offset-based paddle control
    private var touchStartX = 0f
    private var paddleStartX = 0f
    private var touchActive = false

    var readyForRestart = false

    // Derived layout
    private var brickW = 0f; private var brickH = 0f
    private var brickTop = 0f; private var paddleY = 0f

    @Volatile private var running = false
    private var gameThread: Thread? = null

    // Paints
    private val bgPaint = Paint().apply { color = Color.parseColor("#0f1117") }
    private val brickPaints = mapOf(
        1 to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2ecc71") },
        2 to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#f1c40f") },
        3 to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#e74c3c") }
    )
    private val brickStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0f1117"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4f8ef7") }
    private val powerUpPaints = mapOf(
        PowerUpType.WIDE_PADDLE to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9b59b6") },
        PowerUpType.SLOW_BALL   to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00d4ff") },
        PowerUpType.MULTI_BALL  to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#f1c40f") },
        PowerUpType.LASER       to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#e74c3c") }
    )
    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#604f8ef7"); style = Paint.Style.STROKE; strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val overlayPaint = Paint().apply { color = Color.parseColor("#CC0f1117") }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4f8ef7"); typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6b7a99"); typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }
    private val demoWatermark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80f1c40f"); typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }

    // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Theme ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬

    fun applyTheme(t: com.pocketarcade.GameTheme) {
        bgPaint.color        = t.bg
        paddlePaint.color    = t.player
        ballPaint.color      = t.collect
        overlayPaint.color   = t.overlay
        centerPaint.color    = t.accent
        subPaint.color       = t.muted
        brickStroke.color    = t.bg
        aimPaint.color       = t.player.withAlpha(96)
        brickPaints[1]?.color = t.brick1
        brickPaints[2]?.color = t.brick2
        brickPaints[3]?.color = t.brick3
    }

    init { holder.addCallback(this) }

    override fun surfaceCreated(h: SurfaceHolder) { startThread() }
    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) { computeLayout(); if (state == BBState.IDLE) initPaddle() }
    override fun surfaceDestroyed(h: SurfaceHolder) { stopThread() }

    private fun startThread() {
        running = true
        gameThread = Thread {
            while (running) {
                val t0 = System.currentTimeMillis()
                update()
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    try { drawGame(canvas) } finally { holder.unlockCanvasAndPost(canvas) }
                }
                val elapsed = System.currentTimeMillis() - t0
                val sleep = (1000L / 60) - elapsed
                if (sleep > 0) Thread.sleep(sleep)
            }
        }.also { it.start() }
    }

    private fun stopThread() { running = false; gameThread?.join(500) }

    private fun computeLayout() {
        val w = width.toFloat(); val h = height.toFloat()
        brickW = w / COLS
        brickH = h * 0.055f
        brickTop = h * 0.04f
        paddleY = h - h * 0.08f
        paddleWBase = w * 0.22f
    }

    private fun initPaddle() {
        paddleW = paddleWBase
        paddleX = width / 2f - paddleW / 2f
    }

    // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Public API ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬

    fun isUserPlaying() = (state == BBState.PLAYING || state == BBState.AIM) && !demoMode
    fun isDemoMode() = demoMode

    fun startGame(demo: Boolean = false) {
        demoMode = demo
        score = 0; level = 0
        ballSpeed = speedForDifficulty(difficulty)
        widePaddleTimer = 0; slowBallTimer = 0; laserTimer = 0
        computeLayout(); initPaddle()
        loadLevel()
        onScoreChanged?.invoke(0)
        onLevelChanged?.invoke(1)
        if (!demo) onGameStarted?.invoke()
    }

    private fun loadLevel() {
        level++
        if (level > LEVELS.size) { state = BBState.WIN; onWin?.invoke(score); return }
        computeLayout(); initPaddle()
        bricks.clear(); powerUps.clear()
        val grid = LEVELS[level - 1]
        for (r in 0 until BRICK_ROWS) {
            for (c in 0 until COLS) {
                val hp = grid[r][c]
                if (hp > 0) bricks.add(Brick(c, r, hp))
            }
        }
        balls.clear()
        balls.add(Ball(width / 2f, paddleY - 20f, 0f, 0f))
        state = BBState.AIM
        aimAngle = -PI.toFloat() / 2f
        onLevelChanged?.invoke(level)
    }

    // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Update ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬

    private fun update() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return

        if (demoMode && (state == BBState.AIM)) {
            // Demo: aim at nearest brick and launch
            val target = bricks.firstOrNull() ?: return
            val tx = target.col * brickW + brickW / 2f
            val ty = brickTop + target.row * brickH + brickH / 2f
            aimAngle = atan2(ty - paddleY, tx - (paddleX + paddleW / 2f))
            if (aimAngle > -0.2f) aimAngle = -PI.toFloat() / 2f
            launchBall()
        }

        if (state == BBState.AIM) {
            // Stick ball on paddle while aiming
            balls.firstOrNull()?.let { b ->
                b.x = paddleX + paddleW / 2f; b.y = paddleY - b.r - 1f
            }
        }

        if (state != BBState.PLAYING) return

        // Power-up timers
        if (widePaddleTimer > 0) {
            widePaddleTimer--
            paddleW = paddleWBase * WIDE_PADDLE_FACTOR
            if (widePaddleTimer == 0) paddleW = paddleWBase
        }
        if (slowBallTimer > 0) {
            slowBallTimer--
        }
        val speed = if (slowBallTimer > 0) ballSpeed * SLOW_BALL_MULTIPLIER else ballSpeed

        // Demo: move paddle toward ball
        if (demoMode) {
            val ball = balls.firstOrNull()
            if (ball != null) {
                val target = ball.x - paddleW / 2f + (-5..5).random()
                paddleX += (target - paddleX).coerceIn(-9f, 9f)
                paddleX = paddleX.coerceIn(0f, w - paddleW)
            }
        }

        // Move balls
        val ballIter = balls.iterator()
        while (ballIter.hasNext()) {
            val b = ballIter.next()
            if (b.vx == 0f && b.vy == 0f) continue

            val prevBy = b.y
            b.x += b.vx * speed / BASE_BALL_SPEED
            b.y += b.vy * speed / BASE_BALL_SPEED

            // Wall bounces
            if (b.x - b.r < 0) { b.x = b.r; b.vx = abs(b.vx) }
            if (b.x + b.r > w) { b.x = w - b.r; b.vx = -abs(b.vx) }
            if (b.y - b.r < 0) { b.y = b.r; b.vy = abs(b.vy) }

            // Ball lost
            if (b.y - b.r > h) { ballIter.remove(); continue }

            // Paddle bounce ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â swept check prevents tunneling at high speed
            if (b.vy > 0 &&
                prevBy + b.r <= paddleY &&
                b.y + b.r > paddleY &&
                b.x + b.r > paddleX && b.x - b.r < paddleX + paddleW
            ) {
                val rel = (b.x - paddleX) / paddleW - 0.5f
                val angle = rel * PI.toFloat() * 0.7f - PI.toFloat() / 2f
                b.vx = cos(angle) * BASE_BALL_SPEED
                b.vy = sin(angle) * BASE_BALL_SPEED
                b.y = paddleY - b.r - 1f
                if (!demoMode) {
                    SoundManager.play(SoundManager.SFX.BB_PADDLE, context)
                    post { performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK) }
                }
            }

            // Brick collisions
            val brickIter = bricks.iterator()
            while (brickIter.hasNext()) {
                val brick = brickIter.next()
                val bx = brick.col * brickW
                val by_ = brickTop + brick.row * brickH
                if (b.x + b.r > bx && b.x - b.r < bx + brickW &&
                    b.y + b.r > by_ && b.y - b.r < by_ + brickH
                ) {
                    // Determine bounce axis
                    val overlapL = b.x + b.r - bx
                    val overlapR = bx + brickW - (b.x - b.r)
                    val overlapT = b.y + b.r - by_
                    val overlapB = by_ + brickH - (b.y - b.r)
                    val minH = min(overlapL, overlapR)
                    val minV = min(overlapT, overlapB)
                    if (minH < minV) b.vx = -b.vx else b.vy = -b.vy

                    brick.hp--
                    if (!demoMode) {
                        SoundManager.play(SoundManager.SFX.BB_BRICK, context)
                        post { performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK) }
                    }
                    if (brick.hp <= 0) {
                        brickIter.remove()
                        score += (4 - min(brick.hp, 0)) * 10
                        onScoreChanged?.invoke(score)
                        // Chance to drop power-up
                        if ((0 until 5).random() == 0) {
                            val type = PowerUpType.values().random()
                            powerUps.add(PowerUp(bx + brickW / 2f, by_ + brickH / 2f, type))
                        }
                    }
                    break
                }
            }
        }

        // Check all balls lost
        if (balls.isEmpty()) {
            state = BBState.GAME_OVER
            if (!demoMode) SoundManager.play(SoundManager.SFX.BB_GAME_OVER, context)
            onGameOver?.invoke(score)
            return
        }

        // Move and collect power-ups
        val puIter = powerUps.iterator()
        while (puIter.hasNext()) {
            val pu = puIter.next()
            pu.y += POWERUP_SPEED
            if (pu.y > h) { puIter.remove(); continue }
            if (pu.y + 12f >= paddleY && pu.x >= paddleX && pu.x <= paddleX + paddleW) {
                applyPowerUp(pu.type)
                if (!demoMode) SoundManager.play(SoundManager.SFX.BB_POWERUP, context)
                puIter.remove()
            }
        }

        // Level clear
        if (bricks.isEmpty()) {
            state = BBState.LEVEL_CLEAR
            if (!demoMode) SoundManager.play(SoundManager.SFX.BB_LEVEL_CLEAR, context)
        }
    }

    private fun applyPowerUp(type: PowerUpType) {
        when (type) {
            PowerUpType.WIDE_PADDLE -> widePaddleTimer = 600
            PowerUpType.SLOW_BALL -> slowBallTimer = 300
            PowerUpType.MULTI_BALL -> {
                val existing = balls.firstOrNull() ?: return
                balls.add(Ball(existing.x, existing.y, -existing.vx * 0.9f, existing.vy))
                balls.add(Ball(existing.x, existing.y, existing.vx * 1.1f, -existing.vy * 0.8f))
            }
            PowerUpType.LASER -> laserTimer = 300
        }
    }

    private fun launchBall() {
        val b = balls.firstOrNull() ?: return
        var vx = cos(aimAngle)
        var vy = sin(aimAngle)
        if (vy > -0.2f) { vy = -0.6f; val mag = sqrt(vx * vx + vy * vy); vx /= mag; vy /= mag }
        b.vx = vx * BASE_BALL_SPEED
        b.vy = vy * BASE_BALL_SPEED
        state = BBState.PLAYING
    }

    // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Touch ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬

    /** Called by both onTouchEvent and the external swipe zone. */
    fun feedTouch(action: Int, x: Float, y: Float) {
        val w = width.toFloat()
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (demoMode) { startGame(false); return }
                touchStartX = x; paddleStartX = paddleX; touchActive = true
                when (state) {
                    BBState.IDLE -> startGame(false)
                    BBState.LEVEL_CLEAR -> loadLevel()
                    BBState.GAME_OVER, BBState.WIN -> if (readyForRestart) startGame(false)
                    else -> {}
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchActive && (state == BBState.AIM || state == BBState.PLAYING)) {
                    paddleX = (paddleStartX + (x - touchStartX)).coerceIn(0f, w - paddleW)
                    if (state == BBState.AIM) {
                        val ball = balls.firstOrNull() ?: return
                        val angle = atan2(y - ball.y, x - ball.x)
                        aimAngle = if (angle > -0.15f) -PI.toFloat() / 2f else angle
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                touchActive = false
                if (state == BBState.AIM) launchBall()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        feedTouch(event.action, event.x, event.y)
        return true
    }

    // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Draw ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬
    private fun drawGame(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Bricks
        for (b in bricks) {
            val bx = b.col * brickW; val by_ = brickTop + b.row * brickH
            val rect = RectF(bx + 1f, by_ + 1f, bx + brickW - 1f, by_ + brickH - 1f)
            val paint = brickPaints[b.hp.coerceIn(1, 3)] ?: brickPaints[1]!!
            // Darken if damaged
            if (b.hp == 2) { val p = Paint(paint); p.alpha = 220; canvas.drawRoundRect(rect, 3f, 3f, p) }
            else canvas.drawRoundRect(rect, 3f, 3f, paint!!)
            canvas.drawRoundRect(rect, 3f, 3f, brickStroke)
        }

        // Power-ups
        for (pu in powerUps) {
            val paint = powerUpPaints[pu.type] ?: continue
            canvas.drawCircle(pu.x, pu.y, 8f, paint)
        }

        // Balls
        for (b in balls) canvas.drawCircle(b.x, b.y, b.r, ballPaint)

        // Aim guide
        if (state == BBState.AIM) {
            val ball = balls.firstOrNull()
            if (ball != null) {
                val guideLen = 80f
                canvas.drawLine(
                    ball.x, ball.y,
                    ball.x + cos(aimAngle) * guideLen, ball.y + sin(aimAngle) * guideLen,
                    aimPaint
                )
            }
        }

        // Paddle
        val rect = RectF(paddleX, paddleY, paddleX + paddleW, paddleY + PADDLE_H)
        val pp = if (widePaddleTimer > 0) Paint(paddlePaint).apply { color = Color.parseColor("#9b59b6") } else paddlePaint
        canvas.drawRoundRect(rect, 6f, 6f, pp)

        // Overlays
        when (state) {
            BBState.IDLE -> {
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
                centerPaint.textSize = h * 0.055f
                canvas.drawText("BRICK BREAKER", w / 2f, h * 0.4f, centerPaint)
                subPaint.textSize = h * 0.032f
                canvas.drawText("TAP TO PLAY", w / 2f, h * 0.52f, subPaint)
            }
            BBState.LEVEL_CLEAR -> {
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
                centerPaint.textSize = h * 0.055f
                centerPaint.color = Color.parseColor("#2ecc71")
                canvas.drawText("LEVEL CLEAR!", w / 2f, h * 0.45f, centerPaint)
                centerPaint.color = Color.parseColor("#4f8ef7")
                subPaint.textSize = h * 0.032f
                canvas.drawText("TAP FOR NEXT LEVEL", w / 2f, h * 0.55f, subPaint)
            }
            BBState.GAME_OVER -> {
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
                centerPaint.textSize = h * 0.055f
                centerPaint.color = Color.parseColor("#e74c3c")
                canvas.drawText("GAME OVER", w / 2f, h * 0.4f, centerPaint)
                centerPaint.color = Color.parseColor("#4f8ef7")
                subPaint.textSize = h * 0.035f
                canvas.drawText("SCORE: $score", w / 2f, h * 0.5f, subPaint)
                if (readyForRestart) canvas.drawText("TAP TO RETRY", w / 2f, h * 0.6f, subPaint)
            }
            BBState.WIN -> {
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
                centerPaint.textSize = h * 0.05f
                centerPaint.color = Color.parseColor("#2ecc71")
                canvas.drawText("YOU WIN!", w / 2f, h * 0.38f, centerPaint)
                centerPaint.color = Color.parseColor("#4f8ef7")
                subPaint.textSize = h * 0.032f
                canvas.drawText("ALL 10 LEVELS CLEARED!", w / 2f, h * 0.48f, subPaint)
                canvas.drawText("SCORE: $score", w / 2f, h * 0.56f, subPaint)
                if (readyForRestart) canvas.drawText("TAP TO PLAY AGAIN", w / 2f, h * 0.64f, subPaint)
            }
            else -> {}
        }

        if (demoMode && (state == BBState.PLAYING || state == BBState.AIM)) {
            demoWatermark.textSize = h * 0.035f
            canvas.drawText("ÃƒÂ¢Ã¢â‚¬â€œÃ‚Â¶ DEMO", w / 2f, h * 0.96f, demoWatermark)
        }
    }
}
