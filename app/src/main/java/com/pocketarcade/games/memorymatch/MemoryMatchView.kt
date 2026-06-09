package com.pocketarcade.games.memorymatch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.pocketarcade.GameTheme
import com.pocketarcade.SoundManager
import com.pocketarcade.darken
import com.pocketarcade.withAlpha

enum class Difficulty(
    val cols: Int, val rows: Int,
    val star3: Int, val star2: Int,
    val label: String
) {
    EASY  (4,  4,  12,  20,  "EASY"),
    MEDIUM(6,  6,  25,  40,  "MEDIUM"),
    HARD  (8,  8,  45,  65,  "HARD")
}

@SuppressLint("ClickableViewAccessibility")
class MemoryMatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        // 84 unique emoji — enough for Hard (72 pairs)
        private val ICON_POOL = listOf(
            "⚡","💎","🔥","⭐","🚀","👾","🎯","💀","🍄","🎮","🔮","🏆",
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮",
            "🐷","🐸","🐵","🐔","🐧","🐦","🦆","🦅","🦉","🦇","🐝","🐛",
            "🌸","🌺","🌻","🌹","🌷","🌴","🌵","🍀","🍁","🌈","⛄","❄",
            "🍎","🍊","🍋","🍇","🍓","🍒","🍑","🥝","🍕","🍔","🌮","🍩",
            "🎲","🎸","🎺","🎵","🎭","🎨","🔑","💡","🎃","🎄","🎆","🎈",
            "🏀","⚽","🏈","⚾","🎾","🏐","🎱","🏓","🥊","🎿","🏹","🎣"
        )

        private const val FLIP_BACK_DELAY    = 900L
        private const val FLIP_ANIM_DURATION = 200L

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

    // ── Theme colours (updated by applyTheme) ─────────────────────────────────

    private var tAccent       = GREEN_GLOW
    private var tCardFlip     = CARD_FLIP
    private var tCardMatch    = CARD_MATCH
    private var tBorderFlip   = BORDER_FLIP
    private var tBorderMatch  = BORDER_MATCH
    private var tBorderDef    = BORDER_DEF

    fun applyTheme(theme: GameTheme) {
        tAccent      = theme.accent
        tCardFlip    = theme.surface or 0xFF000000.toInt()
        tCardMatch   = theme.player.darken(0.20f)
        tBorderFlip  = theme.accent.withAlpha(0x99)
        tBorderMatch = theme.accent
        tBorderDef   = theme.accent.withAlpha(0x44)
        labelPaint.color     = theme.accent
        diffLabelPaint.color = theme.accent
        winTitlePaint.color  = theme.accent
        invalidate()
    }

    // ── Card state ────────────────────────────────────────────────────────────

    data class Card(
        val id: Int, val icon: String,
        var flipped: Boolean = false,
        var matched: Boolean = false,
        var animScale: Float = 1f
    )

    private var cards      = mutableListOf<Card>()
    private var selected   = mutableListOf<Int>()
    private var mismatched = mutableListOf<Int>()

    private var moves      = 0
    private var matchCount = 0
    private var elapsed    = 0
    private var running    = false
    private var won        = false
    private var locked     = false

    // ── Difficulty ────────────────────────────────────────────────────────────

    var difficulty: Difficulty = Difficulty.EASY
        set(value) {
            field = value
            newGame()
            updateLayout(width, height)
            invalidate()
        }

    private val cols  get() = difficulty.cols
    private val rows  get() = difficulty.rows
    private val total get() = cols * rows

    // ── Callbacks ─────────────────────────────────────────────────────────────

    var onGameStarted: (() -> Unit)? = null
    /** [moves] = total flips, [elapsedSecs] = seconds on the clock */
    var onGameWon: ((moves: Int, elapsedSecs: Int) -> Unit)? = null

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

    // ── Paints ────────────────────────────────────────────────────────────────

    private val bgPaint     = Paint().apply { color = BG }
    private val cardPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = TEXT_WHITE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GREEN_GLOW; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE; isFakeBoldText = true
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE; isFakeBoldText = true
    }
    private val diffLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GREEN_GLOW; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
    }
    private val winTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GREEN_GLOW; textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE; isFakeBoldText = true
    }
    private val dimOverlayPaint = Paint().apply { color = 0xCC000000.toInt() }
    private val winStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE; color = GOLD
    }
    private val winStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAAAAA.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
    }
    private val playAgainBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE; isFakeBoldText = true
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private var headerRect    = RectF()
    private var statsRect     = RectF()
    private var gridRect      = RectF()
    private var cardSize      = 0f
    private var cardGap       = 0f
    private var cornerRadius  = 0f
    private var cardRects     = emptyArray<RectF>()
    private var playAgainRect: RectF? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    init { newGame() }

    private fun newGame() {
        val pairsNeeded = total / 2
        val icons = ICON_POOL.shuffled().take(pairsNeeded)
        val deck  = (icons + icons).shuffled()
        cards = deck.mapIndexed { i, icon -> Card(id = i, icon = icon) }.toMutableList()
        selected.clear(); mismatched.clear()
        moves = 0; matchCount = 0; elapsed = 0
        running = false; won = false; locked = false
        playAgainRect = null
        handler.removeCallbacks(tickRunnable)
        invalidate()
    }

    // ── Game logic ────────────────────────────────────────────────────────────

    private fun onCardTap(index: Int) {
        val card = cards[index]
        if (locked || card.flipped || card.matched) return

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
                cA.matched = true; cB.matched = true
                matchCount++
                selected.clear(); locked = false
                SoundManager.play(SoundManager.SFX.MM_MATCH, context)
                performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)

                if (matchCount == total / 2) {
                    won = true; running = false
                    handler.removeCallbacks(tickRunnable)
                    onGameWon?.invoke(moves, elapsed)
                }
                invalidate()
            } else {
                mismatched.addAll(selected)
                SoundManager.play(SoundManager.SFX.MM_NO_MATCH, context)
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                invalidate()
                // After the "red" display pause, animate the cards back face-down
                handler.postDelayed({ animateFlipBack(cA, cB) }, FLIP_BACK_DELAY)
            }
        } else {
            invalidate()
        }
    }

    private fun animateFlip(index: Int) {
        val card  = cards[index]
        val steps = (FLIP_ANIM_DURATION / 16).toInt().coerceAtLeast(2)
        var step  = 0
        val anim  = object : Runnable {
            override fun run() {
                step++
                val half = steps / 2
                card.animScale = if (step <= half) 1f - step.toFloat() / half
                                 else (step - half).toFloat() / half
                invalidate()
                if (step < steps) handler.postDelayed(this, 16)
                else { card.animScale = 1f; invalidate() }
            }
        }
        handler.post(anim)
    }

    /** Squish both cards to 0 (still showing face), flip them back at the midpoint,
     *  then grow them back to full size showing the card-back face. */
    private fun animateFlipBack(cA: Card, cB: Card) {
        val steps = (FLIP_ANIM_DURATION / 16).toInt().coerceAtLeast(2)
        val half  = steps / 2
        var step  = 0
        val anim  = object : Runnable {
            override fun run() {
                step++
                val scale = if (step <= half) 1f - step.toFloat() / half
                            else (step - half).toFloat() / half
                // At the midpoint: switch to the face-down state
                if (step == half) {
                    cA.flipped = false; cB.flipped = false
                    mismatched.clear()
                }
                cA.animScale = scale; cB.animScale = scale
                invalidate()
                if (step < steps) handler.postDelayed(this, 16)
                else {
                    cA.animScale = 1f; cB.animScale = 1f
                    selected.clear(); locked = false
                    invalidate()
                }
            }
        }
        handler.post(anim)
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateLayout(w, h)
    }

    private fun updateLayout(w: Int, h: Int) {
        if (w == 0 || h == 0) return

        // Gap and padding scale down for denser grids so more cards fit
        val pad = w * when (difficulty) {
            Difficulty.EASY   -> 0.04f
            Difficulty.MEDIUM -> 0.03f
            Difficulty.HARD   -> 0.02f
        }
        cardGap = w * when (difficulty) {
            Difficulty.EASY   -> 0.025f
            Difficulty.MEDIUM -> 0.015f
            Difficulty.HARD   -> 0.008f
        }
        cardSize     = (w - pad * 2f - cardGap * (cols - 1)) / cols
        cornerRadius = cardSize * 0.12f

        val headerH = h * when (difficulty) {
            Difficulty.EASY   -> 0.10f
            Difficulty.MEDIUM -> 0.08f
            Difficulty.HARD   -> 0.07f
        }
        val statsH = h * when (difficulty) {
            Difficulty.EASY   -> 0.09f
            Difficulty.MEDIUM -> 0.08f
            Difficulty.HARD   -> 0.07f
        }

        headerRect = RectF(pad, pad, w - pad, pad + headerH)
        statsRect  = RectF(
            pad,
            headerRect.bottom + pad * 0.4f,
            w - pad,
            headerRect.bottom + pad * 0.4f + statsH
        )
        val gridTop = statsRect.bottom + pad * 0.6f
        gridRect = RectF(
            pad, gridTop,
            pad + cols * cardSize + (cols - 1) * cardGap,
            gridTop + rows * cardSize + (rows - 1) * cardGap
        )

        cardRects = Array(total) { RectF() }
        for (i in 0 until total) {
            val col  = i % cols
            val row  = i / cols
            val left = gridRect.left + col * (cardSize + cardGap)
            val top  = gridRect.top  + row * (cardSize + cardGap)
            cardRects[i] = RectF(left, top, left + cardSize, top + cardSize)
        }

        val titleSize = h * when (difficulty) {
            Difficulty.EASY   -> 0.038f
            Difficulty.MEDIUM -> 0.030f
            Difficulty.HARD   -> 0.025f
        }
        titlePaint.textSize        = titleSize
        diffLabelPaint.textSize    = titleSize * 0.60f
        labelPaint.textSize        = h * when (difficulty) {
            Difficulty.EASY -> 0.016f; Difficulty.MEDIUM -> 0.014f; else -> 0.013f
        }
        valuePaint.textSize        = h * when (difficulty) {
            Difficulty.EASY -> 0.028f; Difficulty.MEDIUM -> 0.024f; else -> 0.022f
        }
        iconPaint.textSize         = cardSize * 0.55f
        winTitlePaint.textSize     = h * 0.046f
        winStarPaint.textSize      = h * 0.044f
        winStatPaint.textSize      = h * 0.022f
        playAgainBtnPaint.textSize = h * 0.022f

        borderPaint.strokeWidth = (cardSize * 0.04f).coerceAtLeast(2f)
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
        val cy = headerRect.centerY()
        canvas.drawText("MEMORY MATCH", w / 2f, cy - titlePaint.textSize * 0.1f, titlePaint)
        canvas.drawText(difficulty.label, w / 2f, cy + diffLabelPaint.textSize * 1.1f, diffLabelPaint)
    }

    private fun drawStats(canvas: Canvas) {
        cardPaint.color = 0xFF111111.toInt()
        canvas.drawRoundRect(statsRect, 16f, 16f, cardPaint)

        val third  = statsRect.width() / 3f
        val labelY = statsRect.top + statsRect.height() * 0.38f
        val valueY = statsRect.top + statsRect.height() * 0.78f

        val mm = elapsed / 60; val ss = elapsed % 60
        listOf("TIME" to "%02d:%02d".format(mm, ss),
               "MOVES" to "%04d".format(moves),
               "PAIRS" to "$matchCount/${total / 2}")
            .forEachIndexed { i, (label, value) ->
                val cx = statsRect.left + third * i + third / 2f
                canvas.drawText(label, cx, labelY, labelPaint)
                canvas.drawText(value, cx, valueY, valuePaint)
            }
    }

    private fun drawGrid(canvas: Canvas) {
        for (i in cards.indices) {
            if (i >= cardRects.size) break
            val card   = cards[i]
            val rect   = cardRects[i]
            val isFlip = card.flipped || card.matched
            val isMiss = mismatched.contains(card.id)

            val sx = card.animScale
            val cx = rect.centerX()
            val scaledRect = RectF(
                cx - rect.width() / 2f * sx, rect.top,
                cx + rect.width() / 2f * sx, rect.bottom
            )

            cardPaint.color = when {
                isMiss       -> CARD_MISS
                card.matched -> tCardMatch
                isFlip       -> tCardFlip
                else         -> CARD_DARK
            }
            canvas.drawRoundRect(scaledRect, cornerRadius, cornerRadius, cardPaint)

            borderPaint.color = when {
                isMiss       -> BORDER_MISS
                card.matched -> tBorderMatch
                isFlip       -> tBorderFlip
                else         -> tBorderDef
            }
            canvas.drawRoundRect(scaledRect, cornerRadius, cornerRadius, borderPaint)

            val textY = rect.centerY() + iconPaint.textSize * 0.35f
            if (isFlip) {
                iconPaint.color = TEXT_WHITE
                canvas.drawText(card.icon, rect.centerX(), textY, iconPaint)
            } else {
                iconPaint.color = TEXT_DIM
                canvas.drawText("?", rect.centerX(), textY, iconPaint)
            }
        }
    }

    private fun drawWinOverlay(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, dimOverlayPaint)

        val boxW = w * 0.78f
        val boxH = h * 0.40f
        val boxRect = RectF(
            (w - boxW) / 2f, (h - boxH) / 2f,
            (w + boxW) / 2f, (h + boxH) / 2f
        )
        cardPaint.color = 0xFF0D1F14.toInt()
        canvas.drawRoundRect(boxRect, 32f, 32f, cardPaint)
        borderPaint.color = tAccent
        canvas.drawRoundRect(boxRect, 32f, 32f, borderPaint)

        val cx = w / 2f

        canvas.drawText("${difficulty.label} CLEAR!", cx, boxRect.top + boxH * 0.24f, winTitlePaint)

        val stars    = when { moves <= difficulty.star3 -> 3; moves <= difficulty.star2 -> 2; else -> 1 }
        canvas.drawText("★".repeat(stars) + "☆".repeat(3 - stars),
            cx, boxRect.top + boxH * 0.44f, winStarPaint)

        val mm = elapsed / 60; val ss = elapsed % 60
        canvas.drawText("%02d:%02d   %d moves".format(mm, ss, moves),
            cx, boxRect.top + boxH * 0.60f, winStatPaint)

        val btnW   = boxW * 0.55f
        val btnH   = boxH * 0.17f
        val btnTop = boxRect.bottom - boxH * 0.22f - btnH
        val btnRect = RectF(cx - btnW / 2f, btnTop, cx + btnW / 2f, btnTop + btnH)
        cardPaint.color = tAccent
        canvas.drawRoundRect(btnRect, 16f, 16f, cardPaint)
        canvas.drawText("PLAY AGAIN",
            cx, btnRect.centerY() + playAgainBtnPaint.textSize * 0.35f, playAgainBtnPaint)
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
            if (cardRects[i].contains(x, y)) { onCardTap(i); return true }
        }
        return true
    }
}
