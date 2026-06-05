package com.pocketarcade.games.cavedriver2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
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
class CaveDiver2View @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    companion object {
        // ── Logical canvas (prototype W / H) ──────────────────────────────
        private const val W = 480f
        private const val H = 320f

        // ── Ship geometry ──────────────────────────────────────────────────
        private const val SHIP_X = 80f
        private const val SHIP_W = 32f
        private const val SHIP_H = 16f

        // ── Physics (exact JSX values) ─────────────────────────────────────
        private const val GRAVITY         = 0.225f
        private const val THRUST          = -0.38f
        private const val DAMPING         = 0.92f
        private const val VY_MIN          = -5f
        private const val VY_MAX          = 6f

        // ── Obstacles ──────────────────────────────────────────────────────
        private const val GAP             = 110f
        private const val PIPE_W          = 44f
        private const val PIPE_SPEED_INIT = 2.4f
        private const val PIPE_INTERVAL   = 110
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

    // ── Pre-allocated Paints ──────────────────────────────────────────────

    // Background gradient (shader set in surfaceChanged)
    private val bgPaint = Paint()

    // Scanline overlay — rgba(0,0,20,0.18) → argb(46, 0, 0, 20)
    private val scanPaint = Paint().apply { color = Color.argb(46, 0, 0, 20) }

    // Stars
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Ship glow — RadialGradient shader set per-draw (position changes)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Thrust flame — LinearGradient shader set per-draw
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Ship body — linear gradient + glow shadow (shadowBlur=10 as in JSX drawShip)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(10f, 0f, 0f, SHIP_COL)
    }

    // Idle-screen ship body — shadowBlur=12 as in JSX paintIdle
    private val idleBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(12f, 0f, 0f, SHIP_COL)
    }

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
        color = SCORE_COL; typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT; isFakeBoldText = true
    }

    // Idle / dead text — shadows match JSX shadowBlur values
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SHIP_COL; typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
        setShadowLayer(24f, 0f, 0f, SHIP_COL)  // JSX shadowBlur=24
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
        color = SCORE_COL; typeface = Typeface.MONOSPACE
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
        // Required for setShadowLayer() to render — hardware-accelerated canvas ignores shadows
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        holder.addCallback(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Preserve 3:2 aspect ratio (480 × 320) so the canvas fills the view exactly
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * H / W).toInt()
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
                try {
                    update()
                    val canvas = holder.lockCanvas()
                    if (canvas != null) {
                        try { drawFrame(canvas) }
                        finally { holder.unlockCanvasAndPost(canvas) }
                    }
                    Thread.sleep(16)   // ~60fps fixed-step cap (matches prototype)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    Log.e("CaveDiver2", "loop error: ${e.message}", e)
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

        // Radial glow
        glowPaint.shader = RadialGradient(x, y, 28f,
            intArrayOf(SHIP_GLOW, SHIP_GLOW, SHIP_GLOW_0),
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

        // Top wall
        caveTopPaint.shader = LinearGradient(p.x, 0f, p.x, p.topH,
            intArrayOf(BG1, CAVE, CAVE_EDGE),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(p.x, 0f, p.x + PIPE_W, p.topH, caveTopPaint)

        // Bottom wall
        caveBotPaint.shader = LinearGradient(p.x, botY, p.x, H,
            intArrayOf(CAVE_EDGE, CAVE, BG1),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(p.x, botY, p.x + PIPE_W, H, caveBotPaint)

        // Stalactite tip — V pointing down (glow via tipPaint.setShadowLayer)
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

        canvas.drawText("CAVE DIVER 2",                   cx, 90f,      titlePaint)
        canvas.drawText("navigate the crystal caverns",   cx, 118f,     subPaint)

        // Idle ship (no cockpit, shadowBlur=12)
        canvas.save()
        glowPaint.shader = RadialGradient(cx, H / 2f, 28f,
            intArrayOf(SHIP_GLOW, SHIP_GLOW, SHIP_GLOW_0),
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

        canvas.drawText("[ TAP TO LAUNCH ]",                     cx, H - 54f, promptPaint)
        canvas.drawText("hold to thrust  ·  release to dive", cx, H - 34f, promptSubPaint)
    }

    /** JSX paintDead() */
    private fun drawDead(canvas: Canvas) {
        drawBg(canvas)
        drawScanlines(canvas)
        val cx = W / 2f

        canvas.drawText("CRASHED",                           cx, 85f,      deadTitlePaint)
        canvas.drawText("FINAL SCORE",                       cx, 130f,     deadSubPaint)
        canvas.drawText("%05d".format(score),                cx, 175f,     deadScorePaint)
        if (bestScore > 0) {
            canvas.drawText("BEST  %05d".format(bestScore), cx, 205f,     deadSubPaint)
        }
        canvas.drawText("[ TAP TO RETRY ]",                 cx, H - 44f,  deadPromptPaint)
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
