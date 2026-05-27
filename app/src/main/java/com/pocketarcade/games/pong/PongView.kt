package com.pocketarcade.games.pong

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.pocketarcade.SoundManager
import kotlin.math.*

enum class PongDifficulty { EASY, MEDIUM, HARD }
enum class PongState { IDLE, COUNTDOWN, PLAYING, GAME_OVER }

class PongView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    companion object {
        const val WIN_SCORE = 7
        const val PADDLE_H = 14f
        const val BALL_R = 14f
        const val PLAYER_SPEED = 18f
    }

    var difficulty = PongDifficulty.MEDIUM
    var onScoreUpdate: ((player: Int, ai: Int) -> Unit)? = null
    var onMatchEnd: ((playerWon: Boolean, playerScore: Int) -> Unit)? = null

    private var state = PongState.IDLE
    private var demoMode = false

    // Game state
    private var bx = 0f; private var by = 0f
    private var bvx = 0f; private var bvy = 0f
    private var playerX = 0f
    private var aiX = 0f
    private var playerScore = 0; private var aiScore = 0
    private var paddleW = 80f
    private var volley = 0
    private var countdownValue = 3

    // Touch
    private var touchX = -1f

    // Demo: virtual "finger" for player paddle
    private var demoTouchX = 0f

    private var gameThread: Thread? = null
    @Volatile private var running = false

    // Paints
    private val bgPaint = Paint().apply { color = Color.parseColor("#0f1117") }
    private val centerLinePaint = Paint().apply {
        color = Color.parseColor("#3a4a6e")
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
    }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val playerPaddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4f8ef7") }
    private val aiPaddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#e74c3c") }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e8ecf0")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6b7a99")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val bigTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4f8ef7")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val overlayPaint = Paint().apply { color = Color.parseColor("#CC0f1117") }
    private val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2ecc71")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val losePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e74c3c")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val demoWatermark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80f1c40f")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    init {
        holder.addCallback(this)
    }

    // ── Surface lifecycle ──────────────────────────────────────────────────────

    override fun surfaceCreated(h: SurfaceHolder) {
        initGame()
        startThread()
    }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        initGame()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        stopThread()
    }

    private fun startThread() {
        running = true
        gameThread = Thread {
            while (running) {
                val t0 = System.currentTimeMillis()
                update()
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    try { drawGame(canvas) }
                    finally { holder.unlockCanvasAndPost(canvas) }
                }
                val elapsed = System.currentTimeMillis() - t0
                val sleep = (1000L / 60) - elapsed
                if (sleep > 0) Thread.sleep(sleep)
            }
        }.also { it.start() }
    }

    private fun stopThread() {
        running = false
        gameThread?.join(500)
    }

    // ── Theme ──────────────────────────────────────────────────────────────────

    fun applyTheme(t: com.pocketarcade.GameTheme) {
        bgPaint.color          = t.bg
        playerPaddlePaint.color = t.player
        aiPaddlePaint.color    = t.rival
        ballPaint.color        = t.collect
        centerLinePaint.color  = t.gridDot
        scorePaint.color       = t.text
        labelPaint.color       = t.muted
        bigTextPaint.color     = t.accent
        overlayPaint.color     = t.overlay
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private fun initGame() {
        val w = width.toFloat()
        val h = height.toFloat()
        paddleW = w * 0.28f
        playerX = w / 2f - paddleW / 2f
        aiX = w / 2f - paddleW / 2f
        demoTouchX = w / 2f
        resetBall()
    }

    private fun resetBall() {
        val w = width.toFloat(); val h = height.toFloat()
        bx = w / 2f; by = h / 2f
        val speed = baseSpeed()
        val angle = Math.toRadians((30 + (0 until 60).random()).toDouble())
        val dir = if ((0..1).random() == 0) 1f else -1f
        bvx = speed * cos(angle).toFloat() * dir
        bvy = speed * sin(angle).toFloat() * if ((0..1).random() == 0) 1f else -1f
        volley = 0
    }

    private fun baseSpeed() = when (difficulty) {
        PongDifficulty.EASY -> 9f
        PongDifficulty.MEDIUM -> 12f
        PongDifficulty.HARD -> 15f
    }

    fun isUserPlaying() = state == PongState.PLAYING && !demoMode
    fun isDemoMode() = demoMode

    fun startGame(demo: Boolean = false) {
        demoMode = demo
        playerScore = 0; aiScore = 0
        state = PongState.PLAYING
        initGame()
        onScoreUpdate?.invoke(0, 0)
    }

    fun setIdle() { state = PongState.IDLE }

    fun feedExternalTouch(x: Float, isDown: Boolean) {
        if (isDown) {
            if (demoMode || state != PongState.PLAYING) startGame(false)
            touchX = x
        } else {
            touchX = -1f
        }
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    private fun update() {
        if (state != PongState.PLAYING) return
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val margin = PADDLE_H + 12f
        val playerY = h - margin
        val aiY = margin

        // Move player paddle (touch or demo AI)
        val targetX = if (demoMode) {
            // Demo: both AI
            bx - paddleW / 2f + (-10..10).random()
        } else {
            if (touchX >= 0) touchX - paddleW / 2f else playerX
        }
        playerX = targetX.coerceIn(0f, w - paddleW)

        // AI paddle
        val aiTargetX = bx - paddleW / 2f + aiJitter()
        val aiSpeed = aiMoveSpeed()
        aiX += (aiTargetX - aiX).coerceIn(-aiSpeed, aiSpeed)
        aiX = aiX.coerceIn(0f, w - paddleW)

        // Move ball
        bx += bvx; by += bvy

        // Wall bounce (left/right)
        if (bx - BALL_R < 0) { bx = BALL_R; bvx = abs(bvx); if (!demoMode) post { performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK) } }
        if (bx + BALL_R > w) { bx = w - BALL_R; bvx = -abs(bvx); if (!demoMode) post { performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK) } }

        // Player paddle collision (bottom)
        if (bvy > 0 && by + BALL_R >= playerY - PADDLE_H / 2f &&
            by + BALL_R <= playerY + PADDLE_H &&
            bx >= playerX && bx <= playerX + paddleW
        ) {
            bvy = -abs(bvy)
            by = playerY - PADDLE_H / 2f - BALL_R
            volley++
            bvx *= 1.03f; bvy *= 1.03f
            if (!demoMode) {
                SoundManager.play(SoundManager.SFX.PONG_BOUNCE, context)
                post { performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK) }
            }
        }

        // AI paddle collision (top)
        if (bvy < 0 && by - BALL_R <= aiY + PADDLE_H / 2f &&
            by - BALL_R >= aiY - PADDLE_H &&
            bx >= aiX && bx <= aiX + paddleW
        ) {
            bvy = abs(bvy)
            by = aiY + PADDLE_H / 2f + BALL_R
            volley++
            bvx *= 1.03f; bvy *= 1.03f
            if (!demoMode) {
                SoundManager.play(SoundManager.SFX.PONG_BOUNCE, context)
                post { performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK) }
            }
        }

        // Score: ball exits top or bottom
        if (by - BALL_R < 0) {
            playerScore++
            onScoreUpdate?.invoke(playerScore, aiScore)
            if (!demoMode) SoundManager.play(SoundManager.SFX.PONG_SCORE, context)
            checkWin()
            if (state == PongState.PLAYING) resetBall()
        } else if (by + BALL_R > h) {
            aiScore++
            onScoreUpdate?.invoke(playerScore, aiScore)
            checkWin()
            if (state == PongState.PLAYING) resetBall()
        }
    }

    private fun checkWin() {
        if (playerScore >= WIN_SCORE) {
            state = PongState.GAME_OVER
            if (!demoMode) SoundManager.play(SoundManager.SFX.PONG_WIN, context)
            onMatchEnd?.invoke(true, playerScore)
        } else if (aiScore >= WIN_SCORE) {
            state = PongState.GAME_OVER
            if (!demoMode) SoundManager.play(SoundManager.SFX.PONG_LOSE, context)
            onMatchEnd?.invoke(false, playerScore)
        }
    }

    private fun aiMoveSpeed() = when (difficulty) {
        PongDifficulty.EASY -> 4f
        PongDifficulty.MEDIUM -> 7f
        PongDifficulty.HARD -> 11f
    }

    private fun aiJitter(): Float = when (difficulty) {
        PongDifficulty.EASY -> ((-30..30).random()).toFloat()
        PongDifficulty.MEDIUM -> ((-8..8).random()).toFloat()
        PongDifficulty.HARD -> ((-2..2).random()).toFloat()
    }

    // ── Touch ──────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (demoMode || state != PongState.PLAYING) startGame(false)
                touchX = event.x
            }
            MotionEvent.ACTION_MOVE -> touchX = event.x
            MotionEvent.ACTION_UP -> touchX = -1f
        }
        return true
    }

    // ── Draw ───────────────────────────────────────────────────────────────────
    private fun drawGame(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Center line
        canvas.drawLine(0f, h / 2f, w, h / 2f, centerLinePaint)

        val margin = PADDLE_H + 12f
        val playerY = h - margin
        val aiY = margin

        // AI paddle
        val aiRect = RectF(aiX, aiY - PADDLE_H, aiX + paddleW, aiY)
        canvas.drawRoundRect(aiRect, 6f, 6f, aiPaddlePaint)

        // Player paddle
        val playerRect = RectF(playerX, playerY, playerX + paddleW, playerY + PADDLE_H)
        canvas.drawRoundRect(playerRect, 6f, 6f, playerPaddlePaint)

        // Ball
        canvas.drawCircle(bx, by, BALL_R, ballPaint)

        // Scores
        scorePaint.textSize = h * 0.07f
        canvas.drawText("$aiScore", w * 0.25f, h * 0.4f, scorePaint)
        canvas.drawText("$playerScore", w * 0.75f, h * 0.6f, scorePaint)

        when (state) {
            PongState.IDLE -> {
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
                bigTextPaint.textSize = h * 0.06f
                canvas.drawText("PONG", w / 2f, h * 0.4f, bigTextPaint)
                labelPaint.textSize = h * 0.035f
                canvas.drawText("TAP TO PLAY", w / 2f, h * 0.5f, labelPaint)
            }
            PongState.GAME_OVER -> {
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
                val playerWon = playerScore >= WIN_SCORE
                val paint = if (playerWon) winPaint else losePaint
                paint.textSize = h * 0.065f
                canvas.drawText(if (playerWon) "YOU WIN!" else "GAME OVER", w / 2f, h * 0.4f, paint)
                labelPaint.textSize = h * 0.035f
                canvas.drawText("$playerScore — $aiScore", w / 2f, h * 0.5f, labelPaint)
                canvas.drawText("TAP TO PLAY AGAIN", w / 2f, h * 0.6f, labelPaint)
            }
            else -> {}
        }

        // Demo watermark
        if (demoMode && state == PongState.PLAYING) {
            demoWatermark.textSize = h * 0.04f
            canvas.drawText("▶ DEMO", w / 2f, h * 0.52f, demoWatermark)
        }
    }
}
