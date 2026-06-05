package com.pocketarcade.games.cavedriver2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.pocketarcade.GameTheme
import com.pocketarcade.withAlpha
import kotlin.math.*
import kotlin.random.Random

private enum class CD2Phase { IDLE, PLAYING, DEAD }

/**
 * Cave Diver 2 — 1:1 Android port of the JSX prototype.
 *
 * Logical canvas: 480 × 320 (matches prototype W × H).
 * Scale = min(viewWidth / 480, viewHeight / 320); canvas centred via translate.
 * LAYER_TYPE_SOFTWARE is required so Paint.setShadowLayer() produces visible glow.
 * Game loop: background thread, Thread.sleep(16) cap — fixed-step physics.
 */
@Suppress("ClickableViewAccessibility")
class CaveDiverView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    companion object {
        // ── Logical canvas — square (480 × 480) ───────────────────────────
        private const val W = 480f
        private const val H = 480f

        // ── Ship geometry ──────────────────────────────────────────────────
        private const val SHIP_X = 80f
        private const val SHIP_W = 32f
        private const val SHIP_H = 16f

        // ── Physics ────────────────────────────────────────────────────────
        private const val GRAVITY         = 0.8f    // continuous downward pull each frame
        private const val THRUST          = -1.6f   // tap impulse; 2× gravity to offset continuous pull
        private const val DAMPING         = 0.92f
        private const val VY_MIN          = -12f
        private const val VY_MAX          = 12f

        // ── Obstacles ──────────────────────────────────────────────────────
        private const val GAP             = 120f
        private const val PIPE_W          = 44f
        private const val PIPE_SPEED_INIT = 6.0f
        private const val PIPE_INTERVAL   = 25
        private const val TIP             = 8f   // tip protrusion into gap

        private const val STAR_COUNT = 40

        // ── Colours (JSX C object, hex-exact) ─────────────────────────────
        private val BG1          = Color.parseColor("#0d0d2b")
        private val BG2          = Color.parseColor("#050510")
        private val CAVE         = Color.parseColor("#1a1a3a")
        private val CAVE_EDGE    = Color.parseColor("#4444aa")
        private val SHIP_COL     = Color.parseColor("#00ffcc")
        // "#00ffcc44"  →  argb(0x44, 0x00, 0xff, 0xcc)
        private val SHIP_GLOW    = Color.argb(0x44, 0x00, 0xFF, 0xCC)
        private val SHIP_GLOW_0  = Color.argb(0,    0x00, 0xFF, 0xCC) // transparent teal
        private val THRUST_B     = Color.parseColor("#ffcc00")
        private val THRUST_A_0   = Color.argb(0, 0xFF, 0x66, 0x00)    // "#ff660000" transparent
        private val BODY_TOP     = Color.parseColor("#aaffee")
        private val BODY_BOT     = Color.parseColor("#00bbaa")
        private val COCKPIT_COL  = Color.parseColor("#001a14")
        private val COCKPIT_STR  = Color.argb(0x88, 0x00, 0xFF, 0xCC) // "#00ffcc88"
        private val HUD_COL      = Color.parseColor("#8888cc")
        private val DEAD_COL     = Color.parseColor("#ff4466")
        private val SCORE_COL    = Color.parseColor("#00ffcc")
    }

    // ── State ─────────────────────────────────────────────────────────────
    private var phase        = CD2Phase.IDLE
    private var shipY        = H / 2f
    private var vy           = 0f
    private var score        = 0
    private var bestScore    = 0
    private var thrust       = false
    private var thrustFrames = 0
    private var frame        = 0
    private var pipeSpeed    = PIPE_SPEED_INIT

    private data class Pipe(var x: Float, val topH: Float, var scored: Boolean = false)
    private data class Star(var x: Float, var y: Float, val r: Float, val spd: Float, var tw: Float)

    private val pipes = mutableListOf<Pipe>()
    private val stars = mutableListOf<Star>()

    // ── Thread safety ──────────────────────────────────────────────────────
    @Volatile private var running      = false
    @Volatile private var startPending = false
    private var gameThread: Thread?    = null

    // ── Public callbacks ───────────────────────────────────────────────────
    var onGameOver:    ((Int) -> Unit)? = null
    var onGameStarted: (() -> Unit)?    = null

    fun loadBestScore(b: Int) { bestScore = b }
    fun isUserPlaying() = phase == CD2Phase.PLAYING

    /**
     * Called by the activity for touch-zone presses as well as in-canvas touches.
     * May be called from the main thread — must NOT touch pipes/stars directly.
     */
    fun setThrusting(pressed: Boolean) {
        if (pressed && phase != CD2Phase.PLAYING) {
            startPending = true           // game thread will call resetGame()
        } else if (phase == CD2Phase.PLAYING) {
            thrust = pressed
        } else {
            thrust = false
        }
    }

    // ── Scale / letterbox ─────────────────────────────────────────────────
    private var scale   = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // ── Theme ─────────────────────────────────────────────────────────────
    private var themeShipCol  = SHIP_COL
    private var themeGlow     = SHIP_GLOW
    private var themeGlow0    = SHIP_GLOW_0
    private var themeHudVal   = SHIP_COL
    private var themeCaveEdge = CAVE_EDGE   // glowing stalactite tip / edge
    private var themeCave     = CAVE        // cave wall body tint

    fun applyTheme(theme: GameTheme) {
        themeShipCol  = theme.player
        themeGlow     = theme.player.withAlpha(0x44)
        themeGlow0    = theme.player.withAlpha(0)
        themeHudVal   = theme.player
        themeCaveEdge = theme.player.withAlpha(0xCC)
        themeCave     = theme.muted.withAlpha(0xBB)
        hudValuePaint.color = themeHudVal
        titlePaint.apply    { color = themeShipCol; setShadowLayer(24f, 0f, 0f, themeShipCol) }
        deadScorePaint.apply { color = themeShipCol; setShadowLayer(16f, 0f, 0f, themeShipCol) }
        bodyPaint.setShadowLayer(10f, 0f, 0f, themeShipCol)
        idleBodyPaint.setShadowLayer(12f, 0f, 0f, themeShipCol)
        cockpitStroke.color = themeShipCol.withAlpha(0x88)
        tipPaint.color = themeCaveEdge
        tipPaint.setShadowLayer(6f, 0f, 0f, theme.player)
    }

    // ── Pre-allocated Paints ──────────────────────────────────────────────

    // Background gradient (shader set in surfaceChanged; fallback color used until then)
    private val bgPaint = Paint().apply { color = BG2 }

    // Scanline overlay — rgba(0,0,20,0.18) → argb(46, 0, 0, 20)
    private val scanPaint = Paint().apply { color = Color.argb(46, 0, 0, 20) }

    // Stars
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Ship glow — RadialGradient shader set per-draw (position changes)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Thrust flame — LinearGradient shader set per-draw
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Ship body — linear gradient + glow shadow (set via applyTheme)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Idle-screen ship body — shadow set via applyTheme
    private val idleBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Cockpit
    private val cockpitFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COCKPIT_COL; style = Paint.Style.FILL
    }
    private val cockpitStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COCKPIT_STR; style = Paint.Style.STROKE; strokeWidth = 1f
    }

    // Cave walls — gradient shader set per pipe
    private val caveTopPaint = Paint()
    private val caveBotPaint = Paint()

    // Cave tips — stroke + glow shadow (shadowBlur=6, JSX ctx.shadowBlur=6)
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CAVE_EDGE; style = Paint.Style.STROKE; strokeWidth = 1.5f
        setShadowLayer(6f, 0f, 0f, CAVE_EDGE)
    }

    // HUD
    private val hudLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HUD_COL; typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT; isFakeBoldText = true
    }
    private val hudValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SCORE_COL; typeface = Typeface.MONOSPACE  // updated in applyTheme
        textAlign = Paint.Align.LEFT; isFakeBoldText = true
    }

    // Idle / dead text — shadows match JSX shadowBlur values
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SHIP_COL; typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
        setShadowLayer(24f, 0f, 0f, SHIP_COL)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HUD_COL; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }
    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
        setShadowLayer(8f, 0f, 0f, Color.WHITE)
    }
    private val promptSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HUD_COL; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }
    private val deadTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEAD_COL; typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
        setShadowLayer(30f, 0f, 0f, DEAD_COL)
    }
    private val deadSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HUD_COL; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }
    private val deadScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SCORE_COL; typeface = Typeface.MONOSPACE  // updated in applyTheme
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
        setShadowLayer(16f, 0f, 0f, SCORE_COL)
    }
    private val deadPromptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
        setShadowLayer(8f, 0f, 0f, Color.WHITE)
    }

    // Reusable paths
    private val shipPath  = Path()
    private val flamePath = Path()
    private val tipPath   = Path()

    // ── SurfaceHolder.Callback ─────────────────────────────────────────────

    init {
        // NOTE: setLayerType(LAYER_TYPE_SOFTWARE) must NOT be set on a SurfaceView —
        // it breaks surface compositing (black screen). SurfaceView.lockCanvas() already
        // returns a software canvas, so setShadowLayer() works natively without this call.
        holder.addCallback(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 1:1 square canvas (480 × 480) — fills width, matches height
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = w
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
    }

    override fun surfaceCreated(h: SurfaceHolder) { startThread() }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        scale   = minOf(w / W, h2 / H)
        offsetX = (w  - W * scale) / 2f
        offsetY = (h2 - H * scale) / 2f

        // Background gradient: top → bottom, exactly as JSX createLinearGradient(0,0,0,H)
        bgPaint.shader = LinearGradient(0f, 0f, 0f, H, BG1, BG2, Shader.TileMode.CLAMP)

        // Text sizes in logical canvas pixels — canvas.scale() applies physical scaling
        titlePaint.textSize      = 36f
        subPaint.textSize        = 13f
        promptPaint.textSize     = 14f
        promptSubPaint.textSize  = 11f
        hudLabelPaint.textSize   = 13f
        hudValuePaint.textSize   = 20f
        deadTitlePaint.textSize  = 38f
        deadSubPaint.textSize    = 13f
        deadScorePaint.textSize  = 40f
        deadPromptPaint.textSize = 14f

        if (stars.isEmpty()) initStars()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) { stopThread() }

    // ── Thread ────────────────────────────────────────────────────────────

    private fun startThread() {
        running = true
        gameThread = Thread {
            while (running) {
                val frameStart = System.nanoTime()
                try {
                    update()
                    val canvas = holder.lockCanvas()
                    if (canvas != null) {
                        try { drawFrame(canvas) }
                        finally { holder.unlockCanvasAndPost(canvas) }
                    }
                    // Adaptive sleep: target 16 ms per frame, subtract actual elapsed time
                    // so physics + draw overhead doesn't accumulate into visible lag.
                    val elapsedMs = (System.nanoTime() - frameStart) / 1_000_000L
                    val sleepMs   = 16L - elapsedMs
                    if (sleepMs > 1L) Thread.sleep(sleepMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    Log.e("CaveDiver", "loop error: ${e.message}", e)
                }
            }
        }.also { it.start() }
    }

    private fun stopThread() {
        running = false
        gameThread?.join(500)
        gameThread = null
    }

    // ── Init helpers ──────────────────────────────────────────────────────

    /** Equivalent of JSX makeStars() */
    private fun initStars() {
        stars.clear()
        repeat(STAR_COUNT) {
            stars.add(Star(
                x   = Random.nextFloat() * W,
                y   = Random.nextFloat() * H,
                r   = 0.3f + Random.nextFloat() * 1.2f,
                spd = 0.1f + Random.nextFloat() * 0.3f,
                tw  = Random.nextFloat() * 2f * PI.toFloat()
            ))
        }
    }

    /** Equivalent of JSX launch() reset block */
    private fun resetGame() {
        shipY        = H / 2f
        vy           = 0f
        pipes.clear()
        frame        = 0
        score        = 0
        pipeSpeed    = PIPE_SPEED_INIT
        thrustFrames = 0
        initStars()
    }

    // ── Touch ─────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            // JSX onPD: if not playing → launch (no thrust); if playing → thrust=true
            MotionEvent.ACTION_DOWN -> {
                if (phase != CD2Phase.PLAYING) startPending = true else thrust = true
            }
            // JSX onPU: thrust=false
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> thrust = false
        }
        return true
    }

    // ── Update (game thread only) ─────────────────────────────────────────

    private fun update() {
        // Consume pending start — resetGame() touches pipes/stars so MUST run on game thread
        if (startPending) {
            startPending = false
            resetGame()
            phase = CD2Phase.PLAYING
            post { onGameStarted?.invoke() }
        }

        if (phase != CD2Phase.PLAYING) return

        // ── JSX tick() physics ──────────────────────────────────────────
        frame++
        vy += GRAVITY
        vy *= DAMPING
        if (thrust) { vy += THRUST; thrustFrames = 6 }
        if (thrustFrames > 0) thrustFrames--
        vy = vy.coerceIn(VY_MIN, VY_MAX)
        shipY += vy

        // Spawn pipes
        if (frame % PIPE_INTERVAL == 0) {
            pipes.add(Pipe(x = W + PIPE_W, topH = 40f + Random.nextFloat() * (H - GAP - 80f)))
        }

        // Move + cull + score pipes
        val iter = pipes.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x -= pipeSpeed
            if (p.x <= -PIPE_W - 10f) { iter.remove(); continue }
            if (!p.scored && p.x + PIPE_W < SHIP_X) {
                p.scored  = true
                score++
                pipeSpeed = PIPE_SPEED_INIT   // JSX resets speed on score (always 2.4)
            }
        }

        // Scroll stars (only during play, matching JSX tick())
        for (s in stars) {
            s.x -= s.spd
            if (s.x < 0f) { s.x = W; s.y = Random.nextFloat() * H }
            s.tw += 0.05f
        }

        // Collision → dead
        if (checkCollision()) {
            phase        = CD2Phase.DEAD
            thrust       = false; thrustFrames = 0
            if (score > bestScore) bestScore = score
            initStars()   // JSX: G.stars = makeStars() in paintDead
            val fs = score
            post { onGameOver?.invoke(fs) }
        }
    }

    private fun checkCollision(): Boolean {
        val sy = shipY; val sx = SHIP_X
        // Wall bounds
        if (sy - SHIP_H / 2f < 0f || sy + SHIP_H / 2f > H) return true
        // Pipe hits
        for (p in pipes) {
            val inX = sx + SHIP_W / 2f > p.x && sx - SHIP_W / 2f < p.x + PIPE_W
            if (!inX) continue
            val botY = p.topH + GAP
            if (sy - SHIP_H / 2f < p.topH || sy + SHIP_H / 2f > botY) return true
            if (hitTip(p.x, p.topH + TIP, true,  sx, sy)) return true
            if (hitTip(p.x, botY  - TIP, false, sx, sy)) return true
        }
        return false
    }

    /**
     * Port of JSX hitTip(px, tipX, tipY, pointingDown).
     * Note: tipX (= pipe centre x) is computed but unused in the JSX body — omitted here.
     *
     * @param px          left x of the pipe
     * @param tipY        y of the triangle vertex (topH+TIP for stalactite, botY-TIP for stalagmite)
     * @param pointingDown true = stalactite (top wall, tip points down into gap)
     * @param bx / by     ship centre
     */
    private fun hitTip(px: Float, tipY: Float, pointingDown: Boolean, bx: Float, by: Float): Boolean {
        val wallY = if (pointingDown) tipY - TIP else tipY + TIP
        if (bx < px || bx > px + PIPE_W) return false
        val t     = (bx - px) / PIPE_W
        val triT  = if (t < 0.5f) t * 2f else (1f - t) * 2f
        val edgeY = wallY + (tipY - wallY) * triT
        return if (pointingDown) by < edgeY + 4f else by > edgeY - 4f
    }

    // ── Draw helpers ──────────────────────────────────────────────────────

    private fun drawFrame(canvas: Canvas) {
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        when (phase) {
            CD2Phase.IDLE    -> drawIdle(canvas)
            CD2Phase.PLAYING -> drawPlaying(canvas)
            CD2Phase.DEAD    -> drawDead(canvas)
        }
        canvas.restore()
    }

    /** JSX drawBg() */
    private fun drawBg(canvas: Canvas) {
        canvas.drawRect(0f, 0f, W, H, bgPaint)
        for (s in stars) {
            val alpha = (0.4f + 0.4f * sin(s.tw)).coerceIn(0f, 1f)
            starPaint.color = Color.argb((255 * alpha).toInt(), 255, 255, 255)
            canvas.drawCircle(s.x, s.y, s.r, starPaint)
        }
    }

    /** JSX scanlines() */
    private fun drawScanlines(canvas: Canvas) {
        var y = 0f
        while (y < H) {
            canvas.drawRect(0f, y, W, y + 2f, scanPaint)
            y += 4f
        }
    }

    /**
     * JSX drawShip(x, y, thrusting).
     *
     * Glow:    createRadialGradient(x,y,2,x,y,28) → inner r=2, outer r=28.
     *          Replicated with three colour stops: solid at 0 & 2/28, transparent at 1.
     * Flame:   gradient from (x−W/2+4, y) to (x−W/2−14, y); tip flickers with sin(Date.now()/80).
     * Body:    gradient with shadowBlur=10; shadow reset after draw (not on cockpit).
     * Cockpit: ctx.ellipse(x+4, y, 7, 5) → drawOval(x−3, y−5, x+11, y+5).
     */
    private fun drawShip(canvas: Canvas, x: Float, y: Float, thrusting: Boolean) {
        canvas.save()

        // Radial glow (colour follows theme)
        glowPaint.shader = RadialGradient(x, y, 28f,
            intArrayOf(themeGlow, themeGlow, themeGlow0),
            floatArrayOf(0f, 2f / 28f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, 28f, glowPaint)

        // Thrust flame
        if (thrusting) {
            val t      = System.currentTimeMillis() / 80f   // JSX: Date.now()/80
            val baseX  = x - SHIP_W / 2f + 4f
            val tipX   = x - SHIP_W / 2f - 10f - sin(t) * 5f
            val gradEnd = x - SHIP_W / 2f - 14f
            flamePaint.shader = LinearGradient(baseX, y, gradEnd, y,
                THRUST_B, THRUST_A_0, Shader.TileMode.CLAMP)
            flamePath.rewind()
            flamePath.moveTo(baseX, y - 4f)
            flamePath.lineTo(tipX,  y)
            flamePath.lineTo(baseX, y + 4f)
            flamePath.close()
            canvas.drawPath(flamePath, flamePaint)
        }

        // Ship body (gradient + glow shadow on bodyPaint)
        bodyPaint.shader = LinearGradient(
            x - SHIP_W / 2f, y - SHIP_H / 2f,
            x + SHIP_W / 2f, y + SHIP_H / 2f,
            BODY_TOP, BODY_BOT, Shader.TileMode.CLAMP)
        shipPath.rewind()
        shipPath.moveTo(x + SHIP_W / 2f,      y)
        shipPath.lineTo(x - SHIP_W / 2f,      y - SHIP_H / 2f)
        shipPath.lineTo(x - SHIP_W / 2f + 4f, y)
        shipPath.lineTo(x - SHIP_W / 2f,      y + SHIP_H / 2f)
        shipPath.close()
        canvas.drawPath(shipPath, bodyPaint)

        // Cockpit: ctx.ellipse(x+4, y, rx=7, ry=5) → oval(x-3, y-5, x+11, y+5)
        canvas.drawOval(x - 3f, y - 5f, x + 11f, y + 5f, cockpitFill)
        canvas.drawOval(x - 3f, y - 5f, x + 11f, y + 5f, cockpitStroke)

        canvas.restore()
    }

    /**
     * JSX drawCave({x, topH}).
     * Top wall gradient: BG1 → CAVE (70%) → CAVE_EDGE from y=0 to y=topH.
     * Bottom wall:       CAVE_EDGE → CAVE (30%) → BG1 from y=botY to y=H.
     * Tips: open V-paths with glow shadow.
     */
    private fun drawCave(canvas: Canvas, p: Pipe) {
        val botY = p.topH + GAP

        // Top wall — themed: BG1 → themeCave → themeCaveEdge
        caveTopPaint.shader = LinearGradient(p.x, 0f, p.x, p.topH,
            intArrayOf(BG1, themeCave, themeCaveEdge),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(p.x, 0f, p.x + PIPE_W, p.topH, caveTopPaint)

        // Bottom wall — themed: themeCaveEdge → themeCave → BG1
        caveBotPaint.shader = LinearGradient(p.x, botY, p.x, H,
            intArrayOf(themeCaveEdge, themeCave, BG1),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(p.x, botY, p.x + PIPE_W, H, caveBotPaint)

        // Stalactite tip — V pointing down (glow via tipPaint shadow; color set in applyTheme)
        tipPath.rewind()
        tipPath.moveTo(p.x,               p.topH)
        tipPath.lineTo(p.x + PIPE_W / 2f, p.topH + TIP)
        tipPath.lineTo(p.x + PIPE_W,      p.topH)
        canvas.drawPath(tipPath, tipPaint)

        // Stalagmite tip — V pointing up
        tipPath.rewind()
        tipPath.moveTo(p.x,               botY)
        tipPath.lineTo(p.x + PIPE_W / 2f, botY - TIP)
        tipPath.lineTo(p.x + PIPE_W,      botY)
        canvas.drawPath(tipPath, tipPaint)
    }

    // ── Screen renderers ──────────────────────────────────────────────────

    /**
     * JSX paintIdle().
     * Ship drawn inline (not via drawShip): glow + body only (no cockpit), shadowBlur=12.
     * Body path uses SHIP_W/2=16 and SHIP_H/2=8 offsets from centre.
     */
    private fun drawIdle(canvas: Canvas) {
        drawBg(canvas)
        drawScanlines(canvas)
        val cx = W / 2f

        canvas.drawText("CAVE DIVER",                     cx, 110f, titlePaint)
        canvas.drawText("navigate the crystal caverns",   cx, 142f, subPaint)

        // Idle ship (no cockpit, shadowBlur=12)
        canvas.save()
        glowPaint.shader = RadialGradient(cx, H / 2f, 28f,
            intArrayOf(themeGlow, themeGlow, themeGlow0),
            floatArrayOf(0f, 2f / 28f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, H / 2f, 28f, glowPaint)

        idleBodyPaint.shader = LinearGradient(
            cx - 16f, H / 2f - 8f, cx + 16f, H / 2f + 8f,
            BODY_TOP, BODY_BOT, Shader.TileMode.CLAMP)
        shipPath.rewind()
        shipPath.moveTo(cx + 16f, H / 2f)
        shipPath.lineTo(cx - 16f, H / 2f - 8f)
        shipPath.lineTo(cx - 12f, H / 2f)
        shipPath.lineTo(cx - 16f, H / 2f + 8f)
        shipPath.close()
        canvas.drawPath(shipPath, idleBodyPaint)
        canvas.restore()

        canvas.drawText("[ TAP TO LAUNCH ]",              cx, H - 68f, promptPaint)
        canvas.drawText("hold to thrust  ·  release to dive", cx, H - 46f, promptSubPaint)
    }

    /** JSX paintDead() */
    private fun drawDead(canvas: Canvas) {
        drawBg(canvas)
        drawScanlines(canvas)
        val cx = W / 2f

        canvas.drawText("CRASHED",                           cx, 140f, deadTitlePaint)
        canvas.drawText("FINAL SCORE",                       cx, 195f, deadSubPaint)
        canvas.drawText("%05d".format(score),                cx, 248f, deadScorePaint)
        if (bestScore > 0) {
            canvas.drawText("BEST  %05d".format(bestScore), cx, 286f, deadSubPaint)
        }
        canvas.drawText("[ TAP TO RETRY ]",                 cx, H - 56f, deadPromptPaint)
    }

    /** JSX game-playing frame */
    private fun drawPlaying(canvas: Canvas) {
        drawBg(canvas)
        for (p in pipes) drawCave(canvas, p)
        drawShip(canvas, SHIP_X, shipY, thrustFrames > 0)
        drawScanlines(canvas)

        // HUD — left-aligned, matches JSX ctx.textAlign="left"
        canvas.drawText("SCORE",              14f, 22f, hudLabelPaint)
        canvas.drawText("%05d".format(score), 14f, 42f, hudValuePaint)
    }
}
