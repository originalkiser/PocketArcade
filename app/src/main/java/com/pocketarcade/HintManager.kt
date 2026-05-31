package com.pocketarcade

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.storage.PrefsManager

object HintManager {

    fun showToastIfNeeded(context: Context, message: String, hintKey: String) {
        if (PrefsManager.isHintShown(context, hintKey)) return
        PrefsManager.markHintShown(context, hintKey)
        Toast.makeText(context, "💡 $message", Toast.LENGTH_LONG).show()
    }

    fun showIfNeeded(
        activity: AppCompatActivity,
        container: ViewGroup,
        anchor: View,
        message: String,
        hintKey: String
    ) {
        if (PrefsManager.isHintShown(activity, hintKey)) return

        anchor.post {
            val anchorLoc = IntArray(2)
            anchor.getLocationInWindow(anchorLoc)
            val containerLoc = IntArray(2)
            container.getLocationInWindow(containerLoc)

            val dp = activity.resources.displayMetrics.density
            val relY = anchorLoc[1] - containerLoc[1]
            val relX = anchorLoc[0] - containerLoc[0] + anchor.width / 2

            val bubble = TextView(activity).apply {
                text = "💡 $message"
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(
                    (14 * dp).toInt(), (10 * dp).toInt(),
                    (14 * dp).toInt(), (10 * dp).toInt()
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10 * dp
                    setColor(0xEE1A2040.toInt())
                    setStroke((1.5f * dp).toInt(), 0xFF4488FF.toInt())
                }
                elevation = 12 * dp
            }

            val bubbleWidth = (200 * dp).toInt()
            val bubbleHeight = ViewGroup.LayoutParams.WRAP_CONTENT
            val params = FrameLayout.LayoutParams(bubbleWidth, bubbleHeight).apply {
                topMargin = (relY - 72 * dp).toInt().coerceAtLeast(0)
                leftMargin = (relX - bubbleWidth / 2).coerceAtLeast(0)
            }

            if (container is FrameLayout) {
                container.addView(bubble, params)
            } else {
                return@post
            }

            fun dismiss() {
                if (bubble.isAttachedToWindow) container.removeView(bubble)
                PrefsManager.markHintShown(activity, hintKey)
            }

            bubble.setOnClickListener { dismiss() }
            container.postDelayed({ dismiss() }, 5_000)
        }
    }

    /**
     * Shows a floating hint bubble ABOVE [anchor] using a PopupWindow.
     * Works with any layout (no FrameLayout container required).
     * Dismissed on tap or after 5 seconds. Shown at most once (persisted via [hintKey]).
     */
    fun showAboveIfNeeded(
        activity: AppCompatActivity,
        anchor: View,
        message: String,
        hintKey: String
    ) {
        if (PrefsManager.isHintShown(activity, hintKey)) return

        anchor.post {
            val dp = activity.resources.displayMetrics.density
            val width = (220 * dp).toInt()

            val bubble = TextView(activity).apply {
                text = "💡 $message"
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(
                    (14 * dp).toInt(), (10 * dp).toInt(),
                    (14 * dp).toInt(), (10 * dp).toInt()
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10 * dp
                    setColor(0xEE1A2040.toInt())
                    setStroke((1.5f * dp).toInt(), 0xFF4488FF.toInt())
                }
                elevation = 12 * dp
            }

            // Measure so we know the height before showing.
            bubble.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val bubbleH = bubble.measuredHeight

            val popup = PopupWindow(bubble, width, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }

            // Center the bubble over the anchor, positioned above it.
            val xOff = -((width - anchor.width) / 2)
            val yOff = -(anchor.height + bubbleH + (8 * dp).toInt())

            fun dismiss() {
                try { popup.dismiss() } catch (_: Exception) {}
                PrefsManager.markHintShown(activity, hintKey)
            }

            bubble.setOnClickListener { dismiss() }
            popup.showAsDropDown(anchor, xOff, yOff)
            anchor.postDelayed({ dismiss() }, 5_000)
        }
    }
}
