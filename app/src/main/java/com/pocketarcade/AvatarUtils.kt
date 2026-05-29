package com.pocketarcade

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

object AvatarUtils {

    fun buildView(context: Context, emojiIndex: Int, colorIndex: Int, sizeDp: Int = 40): FrameLayout {
        val dp = context.resources.displayMetrics.density
        val size = (sizeDp * dp).toInt()
        val frame = FrameLayout(context)
        frame.layoutParams = ViewGroup.LayoutParams(size, size)
        frame.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AvatarData.COLORS[colorIndex.coerceIn(0, AvatarData.COLORS.lastIndex)])
        }
        val tv = TextView(context).apply {
            text = AvatarData.EMOJIS[emojiIndex.coerceIn(0, AvatarData.EMOJIS.lastIndex)]
            gravity = Gravity.CENTER
            textSize = sizeDp * 0.44f
            layoutParams = FrameLayout.LayoutParams(size, size)
        }
        frame.addView(tv)
        return frame
    }

    fun showAvatarPickerDialog(
        activity: AppCompatActivity,
        currentEmojiIndex: Int,
        currentColorIndex: Int,
        onPicked: (emojiIndex: Int, colorIndex: Int) -> Unit
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_avatar_picker, null)
        val dialog = Dialog(activity)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        val dp = activity.resources.displayMetrics.density
        var selEmoji = currentEmojiIndex.coerceIn(0, AvatarData.EMOJIS.lastIndex)
        var selColor = currentColorIndex.coerceIn(0, AvatarData.COLORS.lastIndex)

        val previewFrame = view.findViewById<FrameLayout>(R.id.avatarPreviewFrame)

        val emojiRows = listOf(
            view.findViewById<LinearLayout>(R.id.emojiRow0),
            view.findViewById<LinearLayout>(R.id.emojiRow1),
            view.findViewById<LinearLayout>(R.id.emojiRow2)
        )
        val colorRows = listOf(
            view.findViewById<LinearLayout>(R.id.colorRow0),
            view.findViewById<LinearLayout>(R.id.colorRow1)
        )

        val emojiViews = mutableListOf<TextView>()
        val colorCircles = mutableListOf<View>()

        fun emojiSelBg() = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6 * dp
            setColor(0x22FFFFFF)
            setStroke((2 * dp).toInt(), Color.WHITE)
        }

        fun colorBg(colorInt: Int, selected: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorInt)
            if (selected) setStroke((3 * dp).toInt(), Color.WHITE)
        }

        fun refreshAll() {
            previewFrame.removeAllViews()
            previewFrame.addView(buildView(activity, selEmoji, selColor, 64))
            emojiViews.forEachIndexed { i, tv -> tv.background = if (i == selEmoji) emojiSelBg() else null }
            colorCircles.forEachIndexed { i, v -> v.background = colorBg(AvatarData.COLORS[i], i == selColor) }
        }

        AvatarData.EMOJIS.forEachIndexed { i, emoji ->
            val rowIdx = i / 4
            val tv = TextView(activity).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { selEmoji = i; refreshAll() }
            }
            emojiViews.add(tv)
            emojiRows[rowIdx].addView(tv)
        }

        AvatarData.COLORS.forEachIndexed { i, color ->
            val rowIdx = i / 4
            val size = (36 * dp).toInt()
            val wrapper = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { selColor = i; refreshAll() }
            }
            val circle = View(activity).apply {
                background = colorBg(color, i == selColor)
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            }
            colorCircles.add(circle)
            wrapper.addView(circle)
            colorRows[rowIdx].addView(wrapper)
        }

        refreshAll()

        view.findViewById<TextView>(R.id.btnAvatarConfirm).setOnClickListener {
            onPicked(selEmoji, selColor)
            dialog.dismiss()
        }
        view.findViewById<TextView>(R.id.btnAvatarCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
