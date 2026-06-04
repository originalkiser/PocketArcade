package com.pocketarcade.games.memorymatch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

@SuppressLint("ClickableViewAccessibility")
class MemoryMatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private val ICONS = listOf(
            "⚡", "💎", "🔥", "⭐", "🚀", "👾",
            "🎯", "💀", "🍄", "🎮", "🔮", "🏆"
        )
        private const val COLS  = 4
        private const val ROWS  = 4
        private const val TOTAL = COLS * ROWS   // 16 cards, 8 pairs

        private const val FLIP_BACK_DELAY    = 900L   // ms before mismatch flips back
        private const val FLIP_ANIM_DURATION = 200L   // ms per full flip animation

        // Colour constants
        private const val BG           = 0xFF0A0A0F.toInt()
        private const val CARD_DARK    = 0xFF0D1F14.toInt()
        private const val CARD_FLIP    = 0xFF0D2E1A.toInt()
        private const val CARD_MATCH   = 0xFF0A2910.toInt()
        private const val CARD_MISS    = 0xFF2A0A0A.toInt()
        private const val BORDER_DEF   = 0xFF1A3A2A.toInt()
        private const val BORDER_FLIP  = 0x9900FF96.toInt()
        private const val BORDER_MATCH = 0xFF00FF96.toInt()
        private const val BORDER_MISS  = 0xFFFF4444.toInt()
        private const val GREEN_GLOW   = 0xFF00FF96.toInt()
        private const val TEXT_WHITE   = 0xFFFFFFFF.toInt()
        private const val TEXT_DIM     = 0xFF1A3A2A.toInt()
        private const val GOLD         = 0xFFFFD700.toInt()
    }

    // ── Card state ────────────────────────────────────────────────────────────

    data class Card(
        val id: Int,
        val icon: String,
        var flipped: Boolean = false,
        var matched: Boolean = false,
        var animScale: Float = 1f   // 1f = full width, 0f = edge-on (mid-flip)
    )

    private var cards      = mutableListOf<Card>()
    private var selected   = mutableListOf<Int>()   // card ids face-up & unmatched
    private var mismatched = mutableListOf<Int>()

    private var moves       = 0
    private var matchCount  = 0
    private var elapsed     = 0     // seconds
    private var running     = false
    private var won         = false
    private var locked      = false // board locked during mismatch delay

    // ── Callbacks ─────────────────────────────────────────────────────────────

    /** Fires on the first card tap of a new game. */
    var onGameStarted: (() -> Unit)? = null

    /** Fires when all pairs are matched; [moves] = total flips used. */
    var onGameWon: ((moves: Int) -> Unit)? = null

    // ── Timer ─────────────────────────────────────────────────────────────────

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (running && !won) {
                elapsed++
                invalidate()
                handler.postDelayed(this, 1_000)
            }
        }
    }

    fun pauseTimer()  { running = false; handler.removeCallbacks(tickRunnable) }
    fun resumeTimer() { if (!won && moves > 0) { running = true; handler.post(tickRunnable) } }

    // ── Paints (all initialized once) ────────────────────────────────────────

    private val bgPaint     = Paint().apply { color = BG }
    private val cardPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color     = TEXT_WHITE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = GREEN_GLOW
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.MONOSPACE
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = TEXT_WHITE
        textAlign      = Paint.Align.CENTER
        typeface       = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = TEXT_WHITE
        textAlign      = Paint.Align.CENTER
        typeface       = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val winTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = GREEN_GLOW
        textAlign      = Paint.Align.CENTER
        typeface       = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val dimOverlayPaint = Paint().apply { color = 0xCC000000.toInt() }

    // Win overlay — separate paints so they don't bleed into the HUD paints
    private val winStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.MONOSPACE
        color     = GOLD
    }
    private val winStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = 0xFFAAAAAA.toInt()
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.MONOSPACE
    }
    private val playAgainBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = Color.BLACK
        textAlign      = Paint.Align.CENTER
        typeface       = Typeface.MONOSPACE
        isFakeBoldText = true
    }

    // ── Layout (computed once in onSizeChanged) ───────────────────────────────

    private var headerRect   = RectF()
    private var statsRect    = RectF()
    private var gridRect     = RectF()
    private var cardSize     = 0f
    private var cardGap      = 0f
    private val cardRects    = Array(TOTAL) { RectF() }
    private var cornerRadius = 0f
    private var playAgainRect: RectF? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    init { newGame() }

    private fun newGame() {
        val icons = ICONS.shuffled().take(TOTAL / 2)
        val deck  = (icons + icons).shuffled()
        cards = deck.mapIndexed { i, icon -> Card(id = i, icon = icon) }.toMutableList()
        selected.clear()
        mismatched.clear()
        moves      = 0
        matchCount = 0
        elapsed    = 0
        running    = false
        won        = false
        locked     = false
        playAgainRect = null
        handler.removeCallbacks(tickRunnable)
        invalidate()
    }

    // ── Game logic ────────────────────────────────────────────────────────────

    private fun onCardTap(index: Int) {
        val card = cards[index]
        if (locked || card.flipped || card.matched) return

        // Start timer on first tap
        if (!running) {
            running = true
            handler.post(tickRunnable)
            onGameStarted?.invoke()
        }

        card.flipped = true
        selected.add(card.id)
        animateFlip(index)

        if (selected.size == 2) {
            locked = true
            moves++
            val cA = cards.first { it.id == selected[0] }
            val cB = cards.first { it.id == selected[1] }

            if (cA.icon == cB.icon) {
                // Match!
                cA.matched = true
                cB.matched = true
                matchCount++
                selected.clear()
                locked = false

                if (matchCount == TOTAL / 2) {
                    won     = true
                    running = false
                    handler.removeCallbacks(tickRunnable)
                    onGameWon?.invoke(moves)
                }
                invalidate()
            } else {
                // Mismatch — flash red, then flip back
                mismatched.addAll(selected)
                invalidate()
                handler.postDelayed({
                    cA.flipped = false
                    cB.flipped = false
                    mismatched.clear()
                    selected.clear()
                    locked = false
                    invalidate()
                }, FLIP_BACK_DELAY)
            }
        } else {
            invalidate()
        }
    }

    /** Horizontal-scale animation: squish to 0, then expand back. */
    private fun animateFlip(index: Int) {
        val card  = cards[index]
        val steps = (FLIP_ANIM_DURATION / 16).toInt().coerceAtLeast(2)
        var step  = 0
        val anim  = object : Runnable {
            override fun run() {
                step++
                val half = steps / 2
                card.animScale = if (step <= half) {
                    1f - step.toFloat() / half
                } else {
                    (step - half).toFloat() / half
                }
                invalidate()
                if (step < steps) handler.postDelayed(this, 16)
                else { card.animScale = 1f; invalidate() }
            }
        }
        handler.post(anim)
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad     = w * 0.04f
        val headerH = h * 0.12f
        val statsH  = h * 0.10f
        cardGap     = w * 0.025f
        cardSize    = (w - pad * 2f - cardGap * (COLS - 1)) / COLS
        cornerRadius = cardSize * 0.12f

        headerRect = RectF(pad, pad, w - pad, pad + headerH)
        statsRect  = RectF(
            pad,
            headerRect.bottom + pad * 0.5f,
            w - pad,
            headerRect.bottom + pad * 0.5f + statsH
        )
        val gridTop = statsRect.bottom + pad
        gridRect = RectF(
            pad, gridTop,
            pad + COLS * cardSize + (COLS - 1) * cardGap,
            gridTop + ROWS * cardSize + (ROWS - 1) * cardGap
        )
        for (i in 0 until TOTAL) {
            val col  = i % COLS
            val row  = i / COLS
            val left = gridRect.left + col * (cardSize + cardGap)
            val top  = gridRect.top  + row * (cardSize + cardGap)
            cardRects[i] = RectF(left, top, left + cardSize, top + cardSize)
        }

        // Text sizes in physical pixels
        titlePaint.textSize         = h * 0.040f
        labelPaint.textSize         = h * 0.016f
        valuePaint.textSize         = h * 0.030f
        iconPaint.textSize          = cardSize * 0.50f
        winTitlePaint.textSize      = h * 0.048f
        winStarPaint.textSize       = h * 0.048f
        winStatPaint.textSize       = h * 0.024f
        playAgainBtnPaint.textSize  = h * 0.024f
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        drawHeader(canvas, w)
        drawStats(canvas)
        drawGrid(canvas)
        if (won) drawWinOverlay(canvas, w, h)
    }

    private fun drawHeader(canvas: Canvas, w: Float) {
        canvas.drawText("MEMORY MATCH", w / 2f, headerRect.centerY() + titlePaint.textSize * 0.35f, titlePaint)
    }

    private fun drawStats(canvas: Canvas) {
        // Background pill
        cardPaint.color = 0xFF111111.toInt()
        canvas.drawRoundRect(statsRect, 16f, 16f, cardPaint)

        val third  = statsRect.width() / 3f
        val labelY = statsRect.top + statsRect.height() * 0.38f
        val valueY = statsRect.top + statsRect.height() * 0.78f

        val mm      = elapsed / 60
        val ss      = elapsed % 60
        val timeStr  = "%02d:%02d".format(mm, ss)
        val movesStr = "%03d".format(moves)
        val pairsStr = "$matchCount/${TOTAL / 2}"

        listOf("TIME" to timeStr, "MOVES" to movesStr, "PAIRS" to pairsStr)
            .forEachIndexed { i, (label, value) ->
                val cx = statsRect.left + third * i + third / 2f
                canvas.drawText(label, cx, labelY, labelPaint)
                canvas.drawText(value, cx, valueY, valuePaint)
            }
    }

    private fun drawGrid(canvas: Canvas) {
        for (i in cards.indices) {
            val card      = cards[i]
            val rect      = cardRects[i]
            val isFlipped = card.flipped || card.matched
            val isMiss    = mismatched.contains(card.id)

            // Horizontal scale animation (squish around the card center)
            val sx = card.animScale
            val cx = rect.centerX()
            val scaledRect = RectF(
                cx - rect.width() / 2f * sx, rect.top,
                cx + rect.width() / 2f * sx, rect.bottom
            )

            // Card fill
            cardPaint.color = when {
                isMiss       -> CARD_MISS
                card.matched -> CARD_MATCH
                isFlipped    -> CARD_FLIP
                else         -> CARD_DARK
            }
            canvas.drawRoundRect(scaledRect, cornerRadius, cornerRadius, cardPaint)

            // Border
            borderPaint.color = when {
                isMiss       -> BORDER_MISS
                card.matched -> BORDER_MATCH
                isFlipped    -> BORDER_FLIP
                else         -> BORDER_DEF
            }
            canvas.drawRoundRect(scaledRect, cornerRadius, cornerRadius, borderPaint)

            // Icon / question mark
            val textY = rect.centerY() + iconPaint.textSize * 0.35f
            if (isFlipped) {
                iconPaint.color = TEXT_WHITE  // neutral; emoji use built-in colors
                canvas.drawText(card.icon, rect.centerX(), textY, iconPaint)
            } else {
                iconPaint.color = TEXT_DIM
                canvas.drawText("?", rect.centerX(), textY, iconPaint)
            }
        }
    }

    private fun drawWinOverlay(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, dimOverlayPaint)

        val boxW = w * 0.72f
        val boxH = h * 0.38f
        val boxRect = RectF(
            (w - boxW) / 2f, (h - boxH) / 2f,
            (w + boxW) / 2f, (h + boxH) / 2f
        )

        cardPaint.color = 0xFF0D1F14.toInt()
        canvas.drawRoundRect(boxRect, 32f, 32f, cardPaint)
        borderPaint.color = GREEN_GLOW
        canvas.drawRoundRect(boxRect, 32f, 32f, borderPaint)

        val cx = w / 2f

        // "GAME CLEAR!"
        canvas.drawText("GAME CLEAR!", cx, boxRect.top + boxH * 0.28f, winTitlePaint)

        // Stars (3 = ≤12 moves, 2 = ≤20, 1 = more)
        val stars = when { moves <= 12 -> 3; moves <= 20 -> 2; else -> 1 }
        val starStr = "★".repeat(stars) + "☆".repeat(3 - stars)
        canvas.drawText(starStr, cx, boxRect.top + boxH * 0.52f, winStarPaint)

        // Time + moves stat line
        val mm = elapsed / 60; val ss = elapsed % 60
        canvas.drawText(
            "%02d:%02d   %d MOVES".format(mm, ss, moves),
            cx, boxRect.top + boxH * 0.70f, winStatPaint
        )

        // "PLAY AGAIN" button
        val btnW = boxW * 0.55f
        val btnH = boxH * 0.18f
        val btnTop  = boxRect.bottom - boxH * 0.20f - btnH
        val btnRect = RectF(cx - btnW / 2f, btnTop, cx + btnW / 2f, btnTop + btnH)
        cardPaint.color = GREEN_GLOW
        canvas.drawRoundRect(btnRect, 16f, 16f, cardPaint)
        canvas.drawText(
            "PLAY AGAIN",
            cx, btnRect.centerY() + playAgainBtnPaint.textSize * 0.35f,
            playAgainBtnPaint
        )

        playAgainRect = btnRect
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val x = event.x; val y = event.y

        if (won) {
            playAgainRect?.let { if (it.contains(x, y)) newGame() }
            return true
        }

        for (i in cardRects.indices) {
            if (cardRects[i].contains(x, y)) {
                onCardTap(i)
                return true
            }
        }
        return true
    }
}
