package com.pocketarcade.games.cavedriver

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

private enum class CDState { IDLE, PLAYING, DEAD }

/**
 * Cave Diver — SurfaceView with a background-thread game loop at 60fps.
 *
 * All gameplay values are in the 480×320 logical canvas; a scale matrix
 * maps logical → physical pixels while preserving aspect ratio (letterbox).
 */
@Suppress("ClickableViewAccessibility")
class CaveDiverView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        // Logical canvas size (the "design resolution" — scales to fill the view)
        private const val LOG_W = 480f
        private const val LOG_H = 320f

        // Physics (logical units / frame)
        private const val GRAVITY       = 0.225f
        private const val THRUST        = -0.38f
        private const val DAMPING       = 0.92f
        private const val VY_MIN        = -5f
        private const val VY_MAX        = 6f
        private const val PIPE_SPEED    = 2.4f
        private const val PIPE_INTERVAL = 110     // frames between spawns

        // Dimensions (logical units = dp at mdpi reference)
        private const val PIPE_W   = 44f
        private const val GAP      = 110f
        private const val TIP_H    = 8f
        private const val SHIP_W   = 32f
        private const val SHIP_H   = 16f
        private const val SHIP_X   = 80f          // fixed logical X (≈17 % of canvas)

        private const val STAR_COUNT = 40
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private var state      = CDState.IDLE
    private var shipY      = LOG_H / 2f
    private var vy         = 0f
    private var score      = 0
    private var bestScore  = 0
    private var thrusting  = false
    private var frameCount = 0
    private var time       = 0f

    // ── Internal data holders ─────────────────────────────────────────────────

    private inner class PipeState(var x: Float, val topH: Float, var scored: Boolean = false)

    private inner class StarState(
        var x: Float, var y: Float,
        val radius: Float, val speed: Float,
        var twinkle: Float
    )

    private val pipes = mutableListOf<PipeState>()
    private val stars = mutableListOf<StarState>()

    // ── Callbacks ─────────────────────────────────────────────────────────────

    var onGameOver:    ((Int) -> Unit)? = null
    var onGameStarted: (() -> Unit)?    = null

    fun isUserPlaying() = state == CDState.PLAYING
    fun loadBestScore(b: Int) { bestScore = b }

    // ── Canvas transform (logical → screen) ───────────────────────────────────

    private var scale   = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // ── Thread ────────────────────────────────────────────────────────────────

    @Volatile private var running = false
    private var gameThread: Thread? = null

    // ── Paints (initialized once) ─────────────────────────────────────────────

    private val bgPaint = Paint().apply { color = Color.parseColor("#03030E") }

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val scanPaint = Paint().apply { color = Color.argb(46, 0, 0, 20) }

    private val overlayPaint = Paint().apply { color = Color.argb(140, 3, 3, 14) }

    // Ship
    private val shipFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFCC")
        style = Paint.Style.FILL
    }
    private val shipGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4000FFCC")
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }
    private val cockpitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#007755")
        style = Paint.Style.FILL
    }
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6600")
        style = Paint.Style.FILL
    }

    // Walls
    private val wallPaint = Paint().apply {
        color = Color.parseColor("#0D0D2B")
        style = Paint.Style.FILL
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A3A")
        style = Paint.Style.FILL
    }
    private val glowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA4444AA")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val tipEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4444AA")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // HUD / text
    private val hudLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8888CC")
        typeface = Typeface.MONOSPACE
    }
    private val hudValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFCC")
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFCC")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(14f, 0f, 0f, Color.parseColor("#8800FFCC"))
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8888CC")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subPromptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8888CC")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val crashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4466")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(14f, 0f, 0f, Color.parseColor("#88FF4466"))
    }
    private val crashSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8888CC")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val crashScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFCC")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val crashRetryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    // Reusable paths
    private val shipPath  = Path()
    private val flamePath = Path()
    private val tipPath   = Path()

    // ── SurfaceHolder.Callback ────────────────────────────────────────────────

    init { holder.addCallback(this) }

    override fun surfaceCreated(h: SurfaceHolder) { startThread() }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        val sx = w / LOG_W
        val sy = h2 / LOG_H
        scale   = minOf(sx, sy)
        offsetX = (w  - LOG_W * scale) / 2f
        offsetY = (h2 - LOG_H * scale) / 2f

        // Scale text/stroke sizes so they are correct in physical pixels
        val s = scale
        titlePaint.textSize      = 36f * s
        subtitlePaint.textSize   = 13f * s
        promptPaint.textSize     = 13f * s
        subPromptPaint.textSize  = 10f * s
        hudLabelPaint.textSize   = 10f * s
        hudValuePaint.textSize   = 15f * s
        crashPaint.textSize      = 36f * s
        crashSubPaint.textSize   = 12f * s
        crashScorePaint.textSize = 20f * s
        crashRetryPaint.textSize = 13f * s
        glowStrokePaint.strokeWidth = 3f * s
        tipEdgePaint.strokeWidth    = 1.5f * s
        shipGlowPaint.maskFilter    = BlurMaskFilter(10f * s, BlurMaskFilter.Blur.NORMAL)

        initStars()
        if (state == CDState.IDLE) { shipY = LOG_H / 2f; vy = 0f }
    }

    override fun surfaceDestroyed(h: SurfaceHolder) { stopThread() }

    // ── Thread management ─────────────────────────────────────────────────────

    private fun startThread() {
        running = true
        gameThread = Thread {
            while (running) {
                val t0 = System.currentTimeMillis()
                update()
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    try { drawFrame(canvas) }
                    finally { holder.unlockCanvasAndPost(canvas) }
                }
                val elapsed = System.currentTimeMillis() - t0
                val sleep = 1000L / 60 - elapsed
                if (sleep > 0) Thread.sleep(sleep)
            }
        }.also { it.start() }
    }

    private fun stopThread() {
        running = false
        gameThread?.join(500)
        gameThread = null
    }

    // ── Initialization helpers ────────────────────────────────────────────────

    private fun initStars() {
        stars.clear()
        repeat(STAR_COUNT) {
            stars.add(StarState(
                x       = Random.nextFloat() * LOG_W,
                y       = Random.nextFloat() * LOG_H,
                radius  = 0.3f + Random.nextFloat() * 1.2f,
                speed   = 0.1f + Random.nextFloat() * 0.3f,
                twinkle = Random.nextFloat() * 2f * PI.toFloat()
            ))
        }
    }

    private fun resetGame() {
        score      = 0
        frameCount = 0
        time       = 0f
        shipY      = LOG_H / 2f
        vy         = 0f
        thrusting  = false
        pipes.clear()
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                thrusting = true
                if (state != CDState.PLAYING) {
                    resetGame()
                    state = CDState.PLAYING
                    onGameStarted?.invoke()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> thrusting = false
        }
        return true
    }

    // ── Update (called every frame from game thread) ──────────────────────────

    private fun update() {
        time += 0.05f

        // Stars animate every frame regardless of state
        for (s in stars) {
            s.x -= s.speed
            if (s.x < 0f) s.x = LOG_W
            s.twinkle += 0.05f
        }

        if (state != CDState.PLAYING) return

        frameCount++

        // Ship physics
        vy += if (thrusting) THRUST else GRAVITY
        vy *= DAMPING
        vy  = vy.coerceIn(VY_MIN, VY_MAX)
        shipY += vy

        // Spawn obstacle pair
        if (frameCount % PIPE_INTERVAL == 0) spawnPipe()

        // Move pipes, score, cull
        val toRemove = mutableListOf<PipeState>()
        for (p in pipes) {
            p.x -= PIPE_SPEED
            if (p.x + PIPE_W < 0f) { toRemove.add(p); continue }
            if (!p.scored && p.x + PIPE_W < SHIP_X) { p.scored = true; score++ }
        }
        pipes.removeAll(toRemove)

        // Collision detection
        if (checkCollision()) {
            state     = CDState.DEAD
            thrusting = false
            if (score > bestScore) bestScore = score
            onGameOver?.invoke(score)
        }
    }

    private fun spawnPipe() {
        val minTop  = 40f
        val maxTop  = LOG_H - GAP - 80f   // = 90f
        val topH    = minTop + Random.nextFloat() * (maxTop - minTop)
        pipes.add(PipeState(x = LOG_W + 10f, topH = topH))
    }

    private fun checkCollision(): Boolean {
        val halfH     = SHIP_H / 2f
        val shipLeft  = SHIP_X - SHIP_W / 2f
        val shipRight = SHIP_X + SHIP_W / 2f

        // Ceiling / floor
        if (shipY - halfH < 0f || shipY + halfH > LOG_H) return true

        for (p in pipes) {
            val botY = p.topH + GAP

            // X overlap?
            if (shipRight <= p.x || shipLeft >= p.x + PIPE_W) continue

            // Wall rect collision
            if (shipY - halfH < p.topH || shipY + halfH > botY) return true

            // Ship is in the gap — check pointed tips
            val t    = ((SHIP_X - p.x) / PIPE_W).coerceIn(0f, 1f)
            val triT = if (t < 0.5f) t * 2f else (1f - t) * 2f

            // Top tip points downward: hit if ship center above edge
            val topEdge = p.topH + TIP_H * triT
            if (shipY < topEdge + 4f) return true

            // Bottom tip points upward: hit if ship center below edge
            val botEdge = botY - TIP_H * triT
            if (shipY > botEdge - 4f) return true
        }
        return false
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    private fun drawFrame(canvas: Canvas) {
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, LOG_W, LOG_H)

        // Background
        canvas.drawRect(0f, 0f, LOG_W, LOG_H, bgPaint)
        drawStars(canvas)

        when (state) {
            CDState.IDLE -> {
                drawIdle(canvas)
            }
            CDState.PLAYING -> {
                drawPipes(canvas)
                drawShip(canvas, SHIP_X, shipY, showFlame = thrusting)
                drawHud(canvas)
            }
            CDState.DEAD -> {
                drawPipes(canvas)
                drawShip(canvas, SHIP_X, shipY, showFlame = false)
                drawDead(canvas)
            }
        }

        drawScanlines(canvas)
        canvas.restore()
    }

    private fun drawStars(canvas: Canvas) {
        for (s in stars) {
            val alpha = (0.4f + 0.4f * sin(s.twinkle)).coerceIn(0f, 1f)
            starPaint.color = Color.argb((255 * alpha).toInt(), 160, 170, 220)
            canvas.drawCircle(s.x, s.y, s.radius, starPaint)
        }
    }

    private fun drawPipes(canvas: Canvas) {
        for (p in pipes) {
            val x    = p.x
            val botY = p.topH + GAP

            // Solid wall blocks
            canvas.drawRect(x, 0f, x + PIPE_W, p.topH, wallPaint)
            canvas.drawRect(x, botY, x + PIPE_W, LOG_H, wallPaint)

            // Top stalactite tip (triangle pointing down into gap)
            tipPath.rewind()
            tipPath.moveTo(x,              p.topH)
            tipPath.lineTo(x + PIPE_W / 2f, p.topH + TIP_H)
            tipPath.lineTo(x + PIPE_W,     p.topH)
            tipPath.close()
            // Glow border (slightly larger, drawn first)
            canvas.drawPath(tipPath, glowStrokePaint)
            canvas.drawPath(tipPath, tipPaint)
            canvas.drawPath(tipPath, tipEdgePaint)

            // Bottom stalagmite tip (triangle pointing up into gap)
            tipPath.rewind()
            tipPath.moveTo(x,              botY)
            tipPath.lineTo(x + PIPE_W / 2f, botY - TIP_H)
            tipPath.lineTo(x + PIPE_W,     botY)
            tipPath.close()
            canvas.drawPath(tipPath, glowStrokePaint)
            canvas.drawPath(tipPath, tipPaint)
            canvas.drawPath(tipPath, tipEdgePaint)
        }
    }

    /**
     * Draws the dart-shaped ship.
     * [x] and [y] are the ship center in logical coordinates.
     */
    private fun drawShip(canvas: Canvas, x: Float, y: Float, showFlame: Boolean) {
        // Thrust flame (triangle behind ship, flickering)
        if (showFlame) {
            val flicker = 1f + 0.3f * sin(time * 8f).toFloat()
            val fLen    = SHIP_H * 0.8f * flicker
            flamePath.rewind()
            flamePath.moveTo(x - SHIP_W / 2f,        y - SHIP_H / 4f)
            flamePath.lineTo(x - SHIP_W / 2f - fLen, y)
            flamePath.lineTo(x - SHIP_W / 2f,        y + SHIP_H / 4f)
            flamePath.close()
            canvas.drawPath(flamePath, flamePaint)
        }

        // Glow halo (soft larger shape drawn first)
        shipPath.rewind()
        val gw = 3f
        shipPath.moveTo(x + SHIP_W / 2f + gw, y)
        shipPath.lineTo(x - SHIP_W / 2f - gw, y - SHIP_H / 2f - gw)
        shipPath.lineTo(x - SHIP_W / 2f + 4f, y)
        shipPath.lineTo(x - SHIP_W / 2f - gw, y + SHIP_H / 2f + gw)
        shipPath.close()
        canvas.drawPath(shipPath, shipGlowPaint)

        // Ship body — dart / arrowhead pointing right (4 vertices)
        // Nose:        (x + SHIP_W/2, y)
        // Top back:    (x - SHIP_W/2, y - SHIP_H/2)
        // Engine notch:(x - SHIP_W/2 + 4, y)
        // Bottom back: (x - SHIP_W/2, y + SHIP_H/2)
        shipPath.rewind()
        shipPath.moveTo(x + SHIP_W / 2f,      y)
        shipPath.lineTo(x - SHIP_W / 2f,      y - SHIP_H / 2f)
        shipPath.lineTo(x - SHIP_W / 2f + 4f, y)
        shipPath.lineTo(x - SHIP_W / 2f,      y + SHIP_H / 2f)
        shipPath.close()
        canvas.drawPath(shipPath, shipFillPaint)

        // Cockpit: darker ellipse at (x+4, y), size 14×10 logical units
        canvas.drawOval(
            x + 4f - 7f, y - 5f,
            x + 4f + 7f, y + 5f,
            cockpitPaint
        )
    }

    private fun drawHud(canvas: Canvas) {
        canvas.drawText("SCORE",               12f, 18f, hudLabelPaint)
        canvas.drawText("%05d".format(score),  12f, 34f, hudValuePaint)
    }

    private fun drawIdle(canvas: Canvas) {
        val cx = LOG_W / 2f
        val cy = LOG_H / 2f

        // Title
        canvas.drawText("CAVE DIVER", cx, cy - 66f, titlePaint)
        // Subtitle
        canvas.drawText("navigate the crystal caverns", cx, cy - 48f, subtitlePaint)

        // Ship preview (centered, static)
        drawShip(canvas, cx, cy, showFlame = false)

        // Pulsing thrust prompt
        val pulse = (0.4f + 0.6f * sin(time * 2.0).toFloat()).coerceIn(0f, 1f)
        promptPaint.alpha    = (255 * pulse).toInt()
        subPromptPaint.alpha = (200 * pulse).toInt()
        canvas.drawText("TAP AND HOLD TO THRUST", cx, cy + 50f, promptPaint)
        canvas.drawText("release to dive",        cx, cy + 65f, subPromptPaint)
        promptPaint.alpha    = 255
        subPromptPaint.alpha = 255
    }

    private fun drawDead(canvas: Canvas) {
        val cx = LOG_W / 2f
        val cy = LOG_H / 2f

        // Semi-transparent dim
        canvas.drawRect(0f, 0f, LOG_W, LOG_H, overlayPaint)

        canvas.drawText("CRASHED",            cx, cy - 54f, crashPaint)
        canvas.drawText("FINAL SCORE",        cx, cy - 22f, crashSubPaint)
        canvas.drawText("%05d".format(score), cx, cy + 4f,  crashScorePaint)

        if (bestScore > 0) {
            canvas.drawText("BEST  %05d".format(bestScore), cx, cy + 24f, crashSubPaint)
        }

        // Pulsing retry prompt
        val pulse = (0.4f + 0.6f * sin(time * 2.0).toFloat()).coerceIn(0f, 1f)
        crashRetryPaint.alpha = (255 * pulse).toInt()
        canvas.drawText("TAP TO RETRY", cx, cy + 52f, crashRetryPaint)
        crashRetryPaint.alpha = 255
    }

    private fun drawScanlines(canvas: Canvas) {
        var y = 0f
        while (y < LOG_H) {
            canvas.drawRect(0f, y, LOG_W, y + 2f, scanPaint)
            y += 4f
        }
    }
}
