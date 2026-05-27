package com.pocketarcade.games.asteroids

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.pocketarcade.SoundManager
import com.pocketarcade.withAlpha
import kotlin.math.*
import kotlin.random.Random

data class Asteroid(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val radius: Float,
    val shape: FloatArray,
    var angle: Float = 0f,
    val rotSpeed: Float = 0f
)

data class Bullet(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Int = 90)

data class Ship(
    var x: Float, var y: Float,
    var angle: Float = 0f,
    var vx: Float = 0f, var vy: Float = 0f,
    var lives: Int = 3,
    var invincible: Int = 0
)

enum class AsteroidsState { IDLE, PLAYING, WAVE_CLEAR, GAME_OVER }

class AsteroidsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    companion object {
        const val LARGE_R = 55f
        const val MEDIUM_R = 30f
        const val SMALL_R = 16f
        const val SHIP_SIZE = 20f
        const val BULLET_SPEED = 14f
        const val ROT_SPEED = 4f
        const val THRUST = 0.22f
        const val FRICTION = 0.98f
        const val RAPID_FIRE_INTERVAL = 8
        const val SCORE_LARGE = 20
        const val SCORE_MEDIUM = 50
        const val SCORE_SMALL = 100
    }

    var onScoreChanged: ((Int) -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null

    private var state = AsteroidsState.IDLE
    private var demoMode = false

    private var ship = Ship(0f, 0f)
    private val asteroids = mutableListOf<Asteroid>()
    private val bullets = mutableListOf<Bullet>()
    private var score = 0
    private var wave = 0
    private var waveClearTimer = 0
    private var chainTimer = 0
    private var lastDestroyTime = 0L
    private var chainMultiplier = 1

    // Controls
    private var rotLeft = false
    private var rotRight = false
    private var thrusting = false
    private var firePressed = false
    private var fireTimer = 0

    // Joystick tracking
    private var joystickId = -1
    private var joystickCenterX = 0f; private var joystickCenterY = 0f
    private var joystickDX = 0f; private var joystickDY = 0f
    private var firePointerId = -1

    // Demo AI state
    private var demoTargetAsteroid: Asteroid? = null

    @Volatile private var running = false
    private var gameThread: Thread? = null

    // Paints
    private val bgPaint = Paint().apply { color = Color.parseColor("#0f1117") }
    private val starPaint = Paint().apply { color = Color.parseColor("#3a4a6e") }
    private val shipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00d4ff")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val thrustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e67e22")
        style = Paint.Style.FILL
    }
    private val asteroidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6b7a99")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#f1c40f")
        style = Paint.Style.FILL
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e8ecf0")
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val overlayPaint = Paint().apply { color = Color.parseColor("#CC0f1117") }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4f8ef7")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6b7a99")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val joystickBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#203a4a6e")
        style = Paint.Style.FILL
    }
    private val joystickKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#604f8ef7")
        style = Paint.Style.FILL
    }
    private val fireBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40e74c3c")
        style = Paint.Style.FILL
    }
    private val demoWatermark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80f1c40f")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    // Star field (static)
    private val stars = mutableListOf<PointF>()

    // ── Theme ──────────────────────────────────────────────────────────────────

    fun applyTheme(t: com.pocketarcade.GameTheme) {
        bgPaint.color          = t.bg
        shipPaint.color        = t.player
        asteroidPaint.color    = t.muted
        bulletPaint.color      = t.accent
        starPaint.color        = t.gridDot
        hudPaint.color         = t.text
        centerPaint.color      = t.accent
        subPaint.color         = t.muted
        overlayPaint.color     = t.overlay
        thrustPaint.color      = t.collect
    }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        initStars()
        startThread()
    }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        initStars()
        if (state == AsteroidsState.IDLE) initShip()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) { stopThread() }

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

    private fun initStars() {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        stars.clear()
        repeat(60) { stars.add(PointF((0 until w).random().toFloat(), (0 until h).random().toFloat())) }
    }

    private fun initShip() {
        ship = Ship(width / 2f, height / 2f, -90f * PI.toFloat() / 180f)
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun isUserPlaying() = state == AsteroidsState.PLAYING && !demoMode
    fun isDemoMode() = demoMode

    fun startGame(demo: Boolean = false) {
        demoMode = demo
        score = 0; wave = 0
        initShip()
        asteroids.clear(); bullets.clear()
        spawnWave()
        state = AsteroidsState.PLAYING
        onScoreChanged?.invoke(0)
    }

    // ── Waves ──────────────────────────────────────────────────────────────────

    private fun spawnWave() {
        wave++
        waveClearTimer = 0
        val count = 2 + wave
        repeat(count.coerceAtMost(8)) { spawnLargeAsteroid() }
    }

    private fun spawnLargeAsteroid() {
        val w = width.toFloat(); val h = height.toFloat()
        // Spawn away from ship
        var ax: Float; var ay: Float
        do {
            ax = (0 until width).random().toFloat()
            ay = (0 until height).random().toFloat()
        } while (dist(ax, ay, ship.x, ship.y) < 120f)
        val speed = 1.2f + (0 until 10).random() * 0.2f
        val angle = Random.nextFloat() * PI.toFloat() * 2f
        asteroids.add(makeAsteroid(ax, ay, cos(angle) * speed, sin(angle) * speed, LARGE_R))
    }

    private fun makeAsteroid(x: Float, y: Float, vx: Float, vy: Float, r: Float): Asteroid {
        val pts = 8 + (0 until 5).random()
        val shape = FloatArray(pts * 2)
        for (i in 0 until pts) {
            val a = i.toFloat() / pts * PI.toFloat() * 2f
            val jitter = r * (0.75f + Random.nextFloat() * 0.5f)
            shape[i * 2] = cos(a) * jitter
            shape[i * 2 + 1] = sin(a) * jitter
        }
        return Asteroid(x, y, vx, vy, r, shape, 0f, (Random.nextFloat() - 0.5f) * 2f)
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    private fun update() {
        if (state != AsteroidsState.PLAYING) return
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return

        if (demoMode) demoAI()

        // Omnidirectional thrust: joystick direction = movement direction
        val jMag = sqrt(joystickDX * joystickDX + joystickDY * joystickDY)
        if (jMag > 20f) {
            val nx = joystickDX / jMag
            val ny = joystickDY / jMag
            ship.vx += nx * THRUST
            ship.vy += ny * THRUST
            ship.angle = atan2(joystickDY, joystickDX)
            thrusting = true
        } else {
            thrusting = false
        }
        ship.vx *= FRICTION; ship.vy *= FRICTION

        ship.x = wrap(ship.x + ship.vx, w)
        ship.y = wrap(ship.y + ship.vy, h)

        if (ship.invincible > 0) ship.invincible--

        // Fire
        if (firePressed) {
            fireTimer++
            if (fireTimer == 1 || fireTimer % RAPID_FIRE_INTERVAL == 0) {
                shootBullet()
                if (!demoMode && fireTimer == 1) SoundManager.play(SoundManager.SFX.ASTEROID_SHOOT, context)
            }
        } else {
            fireTimer = 0
        }

        // Move bullets
        val bulletIter = bullets.iterator()
        while (bulletIter.hasNext()) {
            val b = bulletIter.next()
            b.x = wrap(b.x + b.vx, w); b.y = wrap(b.y + b.vy, h)
            b.life--
            if (b.life <= 0) bulletIter.remove()
        }

        // Move asteroids
        asteroids.forEach { a ->
            a.x = wrap(a.x + a.vx, w); a.y = wrap(a.y + a.vy, h)
            a.angle += a.rotSpeed
        }

        // Bullet vs asteroid
        val bulletsToRemove = mutableSetOf<Bullet>()
        val asteroidsToRemove = mutableSetOf<Asteroid>()
        val newAsteroids = mutableListOf<Asteroid>()
        for (b in bullets) {
            for (a in asteroids) {
                if (dist(b.x, b.y, a.x, a.y) < a.radius) {
                    bulletsToRemove.add(b)
                    asteroidsToRemove.add(a)
                    splitAsteroid(a, newAsteroids)
                    val now = System.currentTimeMillis()
                    if (now - lastDestroyTime < 800) chainMultiplier = (chainMultiplier + 1).coerceAtMost(8)
                    else chainMultiplier = 1
                    lastDestroyTime = now
                    val pts = when {
                        a.radius >= LARGE_R -> SCORE_LARGE
                        a.radius >= MEDIUM_R -> SCORE_MEDIUM
                        else -> SCORE_SMALL
                    } * chainMultiplier
                    score += pts
                    onScoreChanged?.invoke(score)
                    if (!demoMode) SoundManager.play(SoundManager.SFX.ASTEROID_EXPLODE, context)
                }
            }
        }
        bullets.removeAll(bulletsToRemove)
        asteroids.removeAll(asteroidsToRemove)
        asteroids.addAll(newAsteroids)

        // Ship vs asteroid collision
        if (ship.invincible == 0) {
            for (a in asteroids) {
                if (dist(ship.x, ship.y, a.x, a.y) < a.radius + SHIP_SIZE * 0.6f) {
                    ship.lives--
                    ship.vx = 0f; ship.vy = 0f
                    ship.invincible = 120
                    if (ship.lives <= 0) {
                        state = AsteroidsState.GAME_OVER
                        if (!demoMode) SoundManager.play(SoundManager.SFX.ASTEROID_GAME_OVER, context)
                        onGameOver?.invoke(score)
                    }
                    break
                }
            }
        }

        // Wave clear
        if (asteroids.isEmpty() && state == AsteroidsState.PLAYING) {
            if (waveClearTimer == 0 && !demoMode) SoundManager.play(SoundManager.SFX.ASTEROID_WAVE_CLEAR, context)
            waveClearTimer++
            if (waveClearTimer > 120) spawnWave()
        }
    }

    private fun splitAsteroid(a: Asteroid, out: MutableList<Asteroid>) {
        val childR = when {
            a.radius >= LARGE_R -> MEDIUM_R
            a.radius >= MEDIUM_R -> SMALL_R
            else -> return
        }
        val speed = (a.vx * a.vx + a.vy * a.vy).pow(0.5f) * 1.4f
        repeat(2) { i ->
            val angle = atan2(a.vy, a.vx) + (if (i == 0) PI.toFloat() / 3 else -PI.toFloat() / 3)
            out.add(makeAsteroid(a.x, a.y, cos(angle) * speed, sin(angle) * speed, childR))
        }
    }

    private fun shootBullet() {
        bullets.add(
            Bullet(
                ship.x + cos(ship.angle) * SHIP_SIZE,
                ship.y + sin(ship.angle) * SHIP_SIZE,
                cos(ship.angle) * BULLET_SPEED + ship.vx,
                sin(ship.angle) * BULLET_SPEED + ship.vy
            )
        )
    }

    private fun demoAI() {
        val target = asteroids.minByOrNull { dist(it.x, it.y, ship.x, ship.y) } ?: return
        val angleToTarget = atan2(target.y - ship.y, target.x - ship.x)
        val d = dist(ship.x, ship.y, target.x, target.y)
        if (d > 120f) {
            joystickDX = cos(angleToTarget) * 50f
            joystickDY = sin(angleToTarget) * 50f
        } else {
            joystickDX = 0f; joystickDY = 0f
        }
        val diff = normalizeAngle(angleToTarget - ship.angle)
        firePressed = abs(diff) < 0.4f
    }

    private fun normalizeAngle(a: Float): Float {
        var r = a
        while (r > PI.toFloat()) r -= (2 * PI).toFloat()
        while (r < -PI.toFloat()) r += (2 * PI).toFloat()
        return r
    }

    private fun wrap(v: Float, max: Float) = ((v % max) + max) % max

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float) =
        sqrt((ax - bx).pow(2) + (ay - by).pow(2))

    // ── Touch ──────────────────────────────────────────────────────────────────
    private val joystickR = 60f
    private val fireBtnR = 50f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (demoMode) { startGame(false); return true }
            if (state != AsteroidsState.PLAYING) { startGame(false); return true }
        }
        val w = width.toFloat(); val h = height.toFloat()
        val fireCX = w * 0.85f; val fireCY = h * 0.8f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val px = event.getX(idx); val py = event.getY(idx)
                val pid = event.getPointerId(idx)
                if (dist(px, py, fireCX, fireCY) < fireBtnR * 1.5f) {
                    firePointerId = pid; firePressed = true
                } else {
                    joystickId = pid; joystickCenterX = px; joystickCenterY = py
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val px = event.getX(i); val py = event.getY(i)
                    if (pid == joystickId) {
                        joystickDX = px - joystickCenterX
                        joystickDY = py - joystickCenterY
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex; val pid = event.getPointerId(idx)
                if (pid == joystickId) {
                    joystickId = -1; joystickDX = 0f; joystickDY = 0f
                    rotLeft = false; rotRight = false; thrusting = false
                }
                if (pid == firePointerId) { firePointerId = -1; firePressed = false }
            }
        }
        return true
    }

    // ── Draw ───────────────────────────────────────────────────────────────────
    private fun drawGame(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Stars
        for (s in stars) canvas.drawCircle(s.x, s.y, 1f, starPaint)

        // Asteroids
        for (a in asteroids) {
            canvas.save()
            canvas.translate(a.x, a.y)
            canvas.rotate(a.angle * 180f / PI.toFloat())
            val path = Path()
            val pts = a.shape.size / 2
            path.moveTo(a.shape[0], a.shape[1])
            for (i in 1 until pts) path.lineTo(a.shape[i * 2], a.shape[i * 2 + 1])
            path.close()
            canvas.drawPath(path, asteroidPaint)
            canvas.restore()
        }

        // Bullets
        for (b in bullets) canvas.drawCircle(b.x, b.y, 3f, bulletPaint)

        // Ship (blink when invincible)
        if (ship.invincible == 0 || ship.invincible % 6 < 3) {
            canvas.save()
            canvas.translate(ship.x, ship.y)
            canvas.rotate(ship.angle * 180f / PI.toFloat())
            val s = SHIP_SIZE
            val shipPath = Path().apply {
                moveTo(s, 0f); lineTo(-s * 0.6f, -s * 0.6f)
                lineTo(-s * 0.3f, 0f); lineTo(-s * 0.6f, s * 0.6f); close()
            }
            canvas.drawPath(shipPath, shipPaint)
            // Thrust flame
            if (thrusting) {
                val thrustPath = Path().apply {
                    moveTo(-s * 0.3f, -s * 0.25f)
                    lineTo(-s * 0.8f - (0..4).random(), 0f)
                    lineTo(-s * 0.3f, s * 0.25f)
                }
                canvas.drawPath(thrustPath, thrustPaint)
            }
            canvas.restore()
        }

        // HUD
        hudPaint.textSize = h * 0.035f
        canvas.drawText("$score", 20f, h * 0.04f + hudPaint.textSize, hudPaint)
        hudPaint.textSize = h * 0.03f
        canvas.drawText("WAVE $wave", w - 20f - hudPaint.measureText("WAVE $wave"), h * 0.04f + hudPaint.textSize, hudPaint)
        val livesStr = "♥".repeat(ship.lives.coerceAtLeast(0))
        canvas.drawText(livesStr, 20f, h * 0.09f + hudPaint.textSize, hudPaint)

        // Virtual joystick
        if (!demoMode) {
            val jcx = w * 0.2f; val jcy = h * 0.8f
            canvas.drawCircle(jcx, jcy, joystickR, joystickBgPaint)
            val knobX = if (joystickId >= 0) (jcx + joystickDX.coerceIn(-joystickR, joystickR)) else jcx
            val knobY = if (joystickId >= 0) (jcy + joystickDY.coerceIn(-joystickR, joystickR)) else jcy
            canvas.drawCircle(knobX, knobY, 22f, joystickKnobPaint)

            val fcx = w * 0.85f; val fcy = h * 0.8f
            val fp = if (firePressed) {
                Paint(fireBtnPaint).apply { color = Color.parseColor("#80e74c3c") }
            } else fireBtnPaint
            canvas.drawCircle(fcx, fcy, fireBtnR, fp)
            hudPaint.textAlign = Paint.Align.CENTER
            hudPaint.textSize = h * 0.025f
            canvas.drawText("FIRE", fcx, fcy + hudPaint.textSize / 3f, hudPaint)
            hudPaint.textAlign = Paint.Align.LEFT
        }

        // Overlays
        when (state) {
            AsteroidsState.IDLE -> {
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
                centerPaint.textSize = h * 0.065f
                canvas.drawText("ASTEROIDS", w / 2f, h * 0.4f, centerPaint)
                subPaint.textSize = h * 0.035f
                canvas.drawText("TAP TO PLAY", w / 2f, h * 0.52f, subPaint)
            }
            AsteroidsState.GAME_OVER -> {
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
                centerPaint.textSize = h * 0.065f
                canvas.drawText("GAME OVER", w / 2f, h * 0.38f, centerPaint)
                subPaint.textSize = h * 0.04f
                canvas.drawText("SCORE: $score", w / 2f, h * 0.48f, subPaint)
                subPaint.textSize = h * 0.03f
                canvas.drawText("TAP TO RESTART", w / 2f, h * 0.58f, subPaint)
            }
            else -> {}
        }

        if (demoMode && state == AsteroidsState.PLAYING) {
            demoWatermark.textSize = h * 0.04f
            canvas.drawText("▶ DEMO", w / 2f, h * 0.96f, demoWatermark)
        }
    }
}
