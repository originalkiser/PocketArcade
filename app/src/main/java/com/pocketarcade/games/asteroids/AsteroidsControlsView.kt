package com.pocketarcade.games.asteroids

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class AsteroidsControlsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var onJoystick: ((dx: Float, dy: Float) -> Unit)? = null
    var onFire: ((pressed: Boolean) -> Unit)? = null

    var autoFireEnabled = false  // set by Activity to show tooltip

    private var joystickId = -1
    private var joystickCenterX = 0f
    private var joystickCenterY = 0f
    private var joystickDX = 0f
    private var joystickDY = 0f
    private var fireId = -1
    private var fireActive = false

    private val r  = 72f   // joystick ring radius (was 54)
    private val kr = 32f   // knob radius (was 24)
    private val fr = 68f   // fire button radius (was 48)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#803a4a6e"); style = Paint.Style.FILL
    }
    private val ringStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#803a4a6e"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#804f8ef7"); style = Paint.Style.FILL
    }
    private val firePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80e74c3c"); style = Paint.Style.FILL
    }
    private val fireActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCe74c3c"); style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80e74c3c"); textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#806b7a99"); textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
    }
    private val autoFirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B300d4ff"); textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        joystickCenterX = w / 4f
        joystickCenterY = h / 2f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val px = event.getX(idx); val py = event.getY(idx)
                val pid = event.getPointerId(idx)
                if (autoFireEnabled || px < w / 2f) {
                    joystickId = pid
                    joystickCenterX = px; joystickCenterY = py
                    joystickDX = 0f; joystickDY = 0f
                    onJoystick?.invoke(0f, 0f)
                } else {
                    fireId = pid; fireActive = true
                    onFire?.invoke(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val px = event.getX(i); val py = event.getY(i)
                    if (pid == joystickId) {
                        joystickDX = px - joystickCenterX
                        joystickDY = py - joystickCenterY
                        onJoystick?.invoke(joystickDX, joystickDY)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex; val pid = event.getPointerId(idx)
                if (pid == joystickId) {
                    joystickId = -1; joystickDX = 0f; joystickDY = 0f
                    onJoystick?.invoke(0f, 0f)
                }
                if (pid == fireId) { fireId = -1; fireActive = false; onFire?.invoke(false) }
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        labelPaint.textSize = h * 0.32f
        hintPaint.textSize  = h * 0.18f
        autoFirePaint.textSize = h * 0.15f

        val defaultJcx = if (autoFireEnabled) w / 2f else w / 4f
        val jcx = if (joystickId >= 0) joystickCenterX else defaultJcx
        val jcy = if (joystickId >= 0) joystickCenterY else h / 2f
        canvas.drawCircle(jcx, jcy, r, ringPaint)
        canvas.drawCircle(jcx, jcy, r, ringStrokePaint)
        val kx = if (joystickId >= 0) (jcx + joystickDX.coerceIn(-r, r)) else jcx
        val ky = if (joystickId >= 0) (jcy + joystickDY.coerceIn(-r, r)) else jcy
        canvas.drawCircle(kx, ky, kr, knobPaint)
        if (joystickId < 0) {
            canvas.drawText("STEER", defaultJcx, h * 0.88f, hintPaint)
        }

        if (autoFireEnabled) {
            canvas.drawText("[ AUTO FIRE ON ]", defaultJcx, h * 0.12f, autoFirePaint)
        } else {
            val divPaint = Paint().apply { color = Color.parseColor("#1a3a4a6e"); strokeWidth = 1f }
            canvas.drawLine(w / 2f, 8f, w / 2f, h - 8f, divPaint)
            val fcx = w * 3f / 4f; val fcy = h / 2f
            canvas.drawCircle(fcx, fcy, fr, if (fireActive) fireActivePaint else firePaint)
            canvas.drawText("FIRE", fcx, h * 0.88f, hintPaint)
        }
    }

    fun applyTheme(accent: Int, muted: Int) {
        knobPaint.color  = (accent and 0x00FFFFFF) or 0x80000000.toInt()
        ringPaint.color  = (muted  and 0x00FFFFFF) or 0x80000000.toInt()
        invalidate()
    }
}
