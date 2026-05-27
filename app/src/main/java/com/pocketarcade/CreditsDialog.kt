package com.pocketarcade

import android.app.Dialog
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

fun showCreditsDialog(activity: AppCompatActivity) {
    val view = activity.layoutInflater.inflate(R.layout.dialog_credits, null)
    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
    view.findViewById<TextView>(R.id.btnCreditsClose).setOnClickListener { dialog.dismiss() }
    dialog.show()
}
