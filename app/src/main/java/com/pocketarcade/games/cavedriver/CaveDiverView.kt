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
 * Logical canvas 480×320 (landscape) — letterboxed into whatever view size is given.
 * Physics and colours match the reference JSX implementation.
 */
@Suppress("ClickableViewAccessibility")
class CaveDiverView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    companion object {
        private const val LOG_W = 480f
        private const val LOG_H = 320f

        // Physics — identical to JSX constants
        private const val GRAVITY       = 0.225f
        private const val THRUST        = -0.38f
        private const val DAMPING       = 0.92f
        private const val VY_MIN        = -5f
        private const val VY_MAX        = 6f
        private const val PIPE_SPEED    = 2.4f
        private const val PIPE_INTERVAL = 110

        // Geometry
        private const val PIPE_W   = 44f
        private const val GAP      = 110f
        private const val TIP_H    = 8f
        private const val SHIP_W   = 32f
        private const val SHIP_H   = 16f
        private const val SHIP_X   = 80f
        private const val STAR_COUNT = 40

        // Palette (matches JSX colour constants)
        private val BG1       = Color.parseColor("#0D0D2B")
        private val BG2       = Color.parseColor("#050510")
        private val CAVE      = Color.parseColor("#1A1A3A")
        private val CAVE_EDGE = Color.parseColor("#4444AA")
        private val SHIP_COL  = Color.parseColor("#00FFCC")
        private val SHIP_GLOW = Color.argb(68, 0, 255, 204)  // #4400FFCC
        private val THRUST_A  = Color.parseColor("#FF6600")
        private val THRUST_B  = Color.parseColor("#FFCC00")
        private val HUD_COL   = Color.parseColor("#8888CC")
        private val DEAD_COL  = Color.parseColor("#FF4466")
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private var state        = CDState.IDLE
    private var shipY        = LOG_H / 2f
    private var vy           = 0f
    private var score        = 0
    private var bestScore    = 0
    private var thrusting    = false
    private var thrustFrames = 0   // flame persistence (match JSX thrustFrames=6)
    private var frameCount   = 0
    private var time         = 0f  // animation clock (0.05 / frame)

    // ── Data ──────────────────────────────────────────────────────────────────

    private inner class PipeState(var x: Float, val topH: Float, var scored: Boolean = false)
    private inner class StarState(var x: Float, var y: Float, val r: Float, val spd: Float, var tw: Float)

    private val pipes = mutableListOf<PipeState>()
    private val stars = mutableListOf<StarState>()

    // ── Callbacks ─────────────────────────────────────────────────────────────

    var onGameOver:    ((Int) -> Unit)? = null
    var onGameStarted: (() -> Unit)?    = null

    fun isUserPlaying() = state == CDState.PLAYING
    fun loadBestScore(b: Int) { bestScore = b }

    /** Called by CaveDiverActivity for both in-canvas touch and control-zone touch. */
    fun setThrusting(pressed: Boolean) {
        if (pressed && state != CDState.PLAYING) {
            resetGame()
            state = CDState.PLAYING
            onGameStarted?.invoke()
        }
        thrusting = pressed
    }

    // ── Scale / letterbox ─────────────────────────────────────────────────────

    private var scale   = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // ── Thread ────────────────────────────────────────────────────────────────

    @Volatile private var running = false
    private var gameThread: Thread? = null

    // ── Paints ────────────────────────────────────────────────────────────────

    private val bgPaint = Paint()  // shader assigned in surfaceChanged

    private val starPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scanPaint  = Paint().apply { color = Color.argb(46, 0, 0, 20) }
    private val dimPaint   = Paint().apply { color = Color.argb(140, 3, 3, 14) }
    private val wallPaint  = Paint()  // shader set per-pipe each frame

    private val tipEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style      = Paint.Style.STROKE
        strokeWidth = 1.5f
        color       = CAVE_EDGE
    }
    private val tipGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        color       = CAVE_EDGE
    }

    // Ship
    private val glowCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = SHIP_GLOW
        style       = Paint.Style.FILL
    }
    private val shipPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cockpitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#001A14")
        style = Paint.Style.FILL
    }
    private val cockpitStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.argb(136, 0, 255, 204)  // #8800FFCC
        style       = Paint.Style.STROKE
        strokeWidth = 1f
    }

    // HUD / text
    private val hudLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = HUD_COL
        typeface = Typeface.MONOSPACE
    }
    private val hudValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = SHIP_COL
        typeface       = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = SHIP_COL
        typeface       = Typeface.MONOSPACE
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(14f, 0f, 0f, Color.argb(136, 0, 255, 204))
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = HUD_COL
        typeface  = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = Color.WHITE
        typeface       = Typeface.MONOSPACE
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(8f, 0f, 0f, Color.WHITE)
    }
    private val subPromptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = HUD_COL
        typeface  = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val crashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = DEAD_COL
        typeface       = Typeface.MONOSPACE
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(20f, 0f, 0f, Color.argb(136, 255, 68, 102))
    }
    private val crashSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = HUD_COL
        typeface  = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val crashScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = SHIP_COL
        typeface       = Typeface.MONOSPACE
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(16f, 0f, 0f, Color.argb(136, 0, 255, 204))
    }
    private val crashRetryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = Color.WHITE
        typeface       = Typeface.MONOSPACE
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Reusable paths
    private val shipPath  = Path()
    private val flamePath = Path()
    private val tipPath   = Path()

    // ── SurfaceHolder.Callback ────────────────────────────────────────────────

    init { holder.addCallback(this) }

    override fun surfaceCreated(h: SurfaceHolder) { startThread() }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        val sx = w  / LOG_W
        val sy = h2 / LOG_H
        scale   = minOf(sx, sy)
        offsetX = (w  - LOG_W * scale) / 2f
        offsetY = (h2 - LOG_H * scale) / 2f

        val s = scale

        // Text sizes (set in physical px; drawn on pre-scaled logical canvas)
        titlePaint.textSize       = 36f * s
        subtitlePaint.textSize    = 13f * s
        promptPaint.textSize      = 14f * s
        subPromptPaint.textSize   = 11f * s
        hudLabelPaint.textSize    = 10f * s
        hudValuePaint.textSize    = 15f * s
        crashPaint.textSize       = 38f * s
        crashSubPaint.textSize    = 13f * s
        crashScorePaint.textSize  = 36f * s
        crashRetryPaint.textSize  = 14f * s

        // Stroke widths in physical px
        tipEdgePaint.strokeWidth  = 1.5f * s
        tipGlowPaint.strokeWidth  = 4f   * s
        tipGlowPaint.maskFilter   = BlurMaskFilter(4f * s, BlurMaskFilter.Blur.NORMAL)
        glowCirclePaint.maskFilter = BlurMaskFilter(20f * s, BlurMaskFilter.Blur.NORMAL)
        cockpitStrokePaint.strokeWidth = 1f * s

        // Background gradient (logical coords — drawn on scaled canvas)
        bgPaint.shader = LinearGradient(0f, 0f, 0f, LOG_H, BG1, BG2, Shader.TileMode.CLAMP)

        initStars()
        if (state == CDState.IDLE) { shipY = LOG_H / 2f; vy = 0f }
    }

    override fun surfaceDestroyed(h: SurfaceHolder) { stopThread() }

    // ── Thread ────────────────────────────────────────────────────────────────

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
                val sleep   = 1000L / 60 - elapsed
                if (sleep > 0) Thread.sleep(sleep)
            }
        }.also { it.start() }
    }

    private fun stopThread() {
        running = false
        gameThread?.join(500)
        gameThread = null
    }

    // ── Init helpers ──────────────────────────────────────────────────────────

    private fun initStars() {
        stars.clear()
        repeat(STAR_COUNT) {
            stars.add(StarState(
                x   = Random.nextFloat() * LOG_W,
                y   = Random.nextFloat() * LOG_H,
                r   = 0.3f + Random.nextFloat() * 1.2f,
                spd = 0.1f + Random.nextFloat() * 0.3f,
                tw  = Random.nextFloat() * 2f * PI.toFloat()
            ))
        }
    }

    private fun resetGame() {
        score        = 0
        frameCount   = 0
        time         = 0f
        shipY        = LOG_H / 2f
        vy           = 0f
        thrusting    = false
        thrustFrames = 0
        pipes.clear()
        initStars()
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN                      -> setThrusting(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setThrusting(false)
        }
        return true
    }

    // ── Update ────────────────────────────────────────────────────────────────

    private fun update() {
        time += 0.05f

        for (s in stars) {
            s.x -= s.spd
            if (s.x < 0f) { s.x = LOG_W; s.y = Random.nextFloat() * LOG_H }
            s.tw += 0.05f
        }

        if (state != CDState.PLAYING) return

        frameCount++

        // Physics: match JSX order — gravity+damping always, thrust added on top
        vy += GRAVITY
        vy *= DAMPING
        if (thrusting) {
            vy += THRUST
            thrustFrames = 6
        } else if (thrustFrames > 0) {
            thrustFrames--
        }
        vy    = vy.coerceIn(VY_MIN, VY_MAX)
        shipY += vy

        if (frameCount % PIPE_INTERVAL == 0) spawnPipe()

        val iter = pipes.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x -= PIPE_SPEED
            if (p.x + PIPE_W < 0f)  { iter.remove(); continue }
            if (!p.scored && p.x + PIPE_W < SHIP_X) { p.scored = true; score++ }
        }

        if (checkCollision()) {
            state        = CDState.DEAD
            thrusting    = false
            thrustFrames = 0
            if (score > bestScore) bestScore = score
            onGameOver?.invoke(score)
        }
    }

    private fun spawnPipe() {
        val topH = 40f + Random.nextFloat() * (LOG_H - GAP - 120f)
        pipes.add(PipeState(x = LOG_W + 10f, topH = topH))
    }

    private fun checkCollision(): Boolean {
        val halfH     = SHIP_H / 2f
        val shipLeft  = SHIP_X - SHIP_W / 2f
        val shipRight = SHIP_X + SHIP_W / 2f
        if (shipY - halfH < 0f || shipY + halfH > LOG_H) return true
        for (p in pipes) {
            val botY = p.topH + GAP
            if (shipRight <= p.x || shipLeft >= p.x + PIPE_W) continue
            if (shipY - halfH < p.topH || shipY + halfH > botY) return true
            val t    = ((SHIP_X - p.x) / PIPE_W).coerceIn(0f, 1f)
            val triT = if (t < 0.5f) t * 2f else (1f - t) * 2f
            if (shipY < p.topH  + TIP_H * triT + 4f) return true
            if (shipY > botY    - TIP_H * triT - 4f) return true
        }
        return false
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    private fun drawFrame(canvas: Canvas) {
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, LOG_W, LOG_H)

        canvas.drawRect(0f, 0f, LOG_W, LOG_H, bgPaint)
        drawStars(canvas)

        when (state) {
            CDState.IDLE    -> drawIdle(canvas)
            CDState.PLAYING -> {
                drawPipes(canvas)
                drawShip(canvas, SHIP_X, shipY, thrustFrames > 0)
                drawHud(canvas)
            }
            CDState.DEAD    -> {
                drawPipes(canvas)
                drawShip(canvas, SHIP_X, shipY, false)
                drawDead(canvas)
            }
        }

        drawScanlines(canvas)
        canvas.restore()
    }

    private fun drawStars(canvas: Canvas) {
        for (s in stars) {
            val alpha = (0.4f + 0.4f * sin(s.tw)).coerceIn(0f, 1f)
            starPaint.color = Color.argb((255 * alpha).toInt(), 160, 170, 220)
            canvas.drawCircle(s.x, s.y, s.r, starPaint)
        }
    }

    private fun drawPipes(canvas: Canvas) {
        for (p in pipes) {
            val botY = p.topH + GAP

            // Top wall: bg1 → (70%) cave → cave_edge
            wallPaint.shader = LinearGradient(
                p.x, 0f, p.x, p.topH,
                intArrayOf(BG1, CAVE, CAVE_EDGE),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(p.x, 0f, p.x + PIPE_W, p.topH, wallPaint)

            // Bottom wall: cave_edge → (30%) cave → bg1
            wallPaint.shader = LinearGradient(
                p.x, botY, p.x, LOG_H,
                intArrayOf(CAVE_EDGE, CAVE, BG1),
                floatArrayOf(0f, 0.3f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(p.x, botY, p.x + PIPE_W, LOG_H, wallPaint)

            // Stalactite tip: V-stroke pointing down (glow then sharp edge)
            tipPath.rewind()
            tipPath.moveTo(p.x,              p.topH)
            tipPath.lineTo(p.x + PIPE_W / 2f, p.topH + TIP_H)
            tipPath.lineTo(p.x + PIPE_W,     p.topH)
            canvas.drawPath(tipPath, tipGlowPaint)
            canvas.drawPath(tipPath, tipEdgePaint)

            // Stalagmite tip: V-stroke pointing up
            tipPath.rewind()
            tipPath.moveTo(p.x,              botY)
            tipPath.lineTo(p.x + PIPE_W / 2f, botY - TIP_H)
            tipPath.lineTo(p.x + PIPE_W,     botY)
            canvas.drawPath(tipPath, tipGlowPaint)
            canvas.drawPath(tipPath, tipEdgePaint)
        }
    }

    private fun drawShip(canvas: Canvas, x: Float, y: Float, showFlame: Boolean) {
        // Radial glow (soft blurred circle matching JSX's radial gradient)
        canvas.drawCircle(x, y, 28f, glowCirclePaint)

        // Thrust flame — gradient triangle with flickering tip length
        if (showFlame) {
            val t      = time * 4f  // faster clock: ~0.2/frame ≈ Date.now()/80 per frame
            val tipX   = x - SHIP_W / 2f - 10f - sin(t) * 5f
            val baseX  = x - SHIP_W / 2f + 4f
            flamePaint.shader = LinearGradient(
                baseX, y, tipX, y,
                THRUST_B, Color.argb(0, 255, 102, 0),   // FFCC00 → transparent FF6600
                Shader.TileMode.CLAMP
            )
            flamePath.rewind()
            flamePath.moveTo(baseX, y - 4f)
            flamePath.lineTo(tipX,  y)
            flamePath.lineTo(baseX, y + 4f)
            flamePath.close()
            canvas.drawPath(flamePath, flamePaint)
        }

        // Ship body — linear gradient #AAFFEE → #00BBAA (matching JSX)
        shipPaint.shader = LinearGradient(
            x - SHIP_W / 2f, y - SHIP_H / 2f,
            x + SHIP_W / 2f, y + SHIP_H / 2f,
            Color.parseColor("#AAFFEE"), Color.parseColor("#00BBAA"),
            Shader.TileMode.CLAMP
        )
        shipPath.rewind()
        shipPath.moveTo(x + SHIP_W / 2f,      y)
        shipPath.lineTo(x - SHIP_W / 2f,      y - SHIP_H / 2f)
        shipPath.lineTo(x - SHIP_W / 2f + 4f, y)
        shipPath.lineTo(x - SHIP_W / 2f,      y + SHIP_H / 2f)
        shipPath.close()
        canvas.drawPath(shipPath, shipPaint)

        // Cockpit (dark fill + translucent teal stroke)
        canvas.drawOval(x - 3f, y - 5f, x + 11f, y + 5f, cockpitPaint)
        canvas.drawOval(x - 3f, y - 5f, x + 11f, y + 5f, cockpitStrokePaint)
    }

    private fun drawHud(canvas: Canvas) {
        canvas.drawText("SCORE",              12f, 16f, hudLabelPaint)
        canvas.drawText("%05d".format(score), 12f, 32f, hudValuePaint)
    }

    private fun drawIdle(canvas: Canvas) {
        val cx = LOG_W / 2f
        val cy = LOG_H / 2f

        canvas.drawText("CAVE DIVER",                  cx, cy - 70f, titlePaint)
        canvas.drawText("navigate the crystal caverns", cx, cy - 42f, subtitlePaint)

        drawShip(canvas, cx, cy, showFlame = false)

        val pulse = (0.4f + 0.6f * sin(time * 2.0).toFloat()).coerceIn(0f, 1f)
        promptPaint.alpha    = (255 * pulse).toInt()
        subPromptPaint.alpha = (200 * pulse).toInt()
        canvas.drawText("TAP AND HOLD TO THRUST",  cx, cy + 106f, promptPaint)
        canvas.drawText("release to dive",          cx, cy + 122f, subPromptPaint)
        promptPaint.alpha    = 255
        subPromptPaint.alpha = 255
    }

    private fun drawDead(canvas: Canvas) {
        val cx = LOG_W / 2f
        val cy = LOG_H / 2f

        canvas.drawRect(0f, 0f, LOG_W, LOG_H, dimPaint)

        canvas.drawText("CRASHED",            cx, cy - 75f, crashPaint)
        canvas.drawText("FINAL SCORE",        cx, cy - 30f, crashSubPaint)
        canvas.drawText("%05d".format(score), cx, cy + 15f, crashScorePaint)

        if (bestScore > 0) {
            canvas.drawText("BEST  %05d".format(bestScore), cx, cy + 45f, crashSubPaint)
        }

        val pulse = (0.4f + 0.6f * sin(time * 2.0).toFloat()).coerceIn(0f, 1f)
        crashRetryPaint.alpha = (255 * pulse).toInt()
        canvas.drawText("TAP TO RETRY", cx, cy + 116f, crashRetryPaint)
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
