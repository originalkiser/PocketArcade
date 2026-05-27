package com.pocketarcade

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.storage.PrefsManager

fun showThemePickerDialog(
    activity: AppCompatActivity,
    game: String,
    onApplied: () -> Unit
) {
    val view = activity.layoutInflater.inflate(R.layout.dialog_theme_picker, null)

    val tvThemeName    = view.findViewById<TextView>(R.id.tvThemeName)
    val switchApplyAll = view.findViewById<Switch>(R.id.switchApplyAll)
    val swatchIds = listOf(
        R.id.swatch0, R.id.swatch1, R.id.swatch2,
        R.id.swatch3, R.id.swatch4, R.id.swatch5
    )

    var localIndex  = ThemeManager.effectiveThemeIndex(activity, game)
    var applyToAll  = PrefsManager.isGameUsingGlobalTheme(activity, game)
    switchApplyAll.isChecked = applyToAll

    fun refreshSwatches() {
        tvThemeName.text = Themes.ALL[localIndex].first.name
        val d = activity.resources.displayMetrics.density
        swatchIds.forEachIndexed { i, id ->
            val sw = view.findViewById<View>(id)
            val gd = GradientDrawable()
            gd.shape = GradientDrawable.OVAL
            gd.setColor(Themes.ALL[i].first.swatch)
            if (i == localIndex) gd.setStroke((3 * d).toInt(), Color.WHITE)
            sw.background = gd
        }
    }

    swatchIds.forEachIndexed { i, id ->
        view.findViewById<View>(id).setOnClickListener {
            localIndex = i
            if (applyToAll) {
                ThemeManager.setThemeIndex(activity, i)
            } else {
                PrefsManager.setGameThemeIndex(activity, game, i)
            }
            refreshSwatches()
            onApplied()
        }
    }

    switchApplyAll.setOnCheckedChangeListener { _, checked ->
        applyToAll = checked
        PrefsManager.setGameUsingGlobalTheme(activity, game, checked)
        if (checked) {
            ThemeManager.setThemeIndex(activity, localIndex)
        } else {
            PrefsManager.setGameThemeIndex(activity, game, localIndex)
        }
    }

    refreshSwatches()

    val dialog = AlertDialog.Builder(activity)
        .setView(view)
        .setPositiveButton("DONE", null)
        .create()
    dialog.window?.setBackgroundDrawableResource(R.color.surface)
    dialog.show()
}
