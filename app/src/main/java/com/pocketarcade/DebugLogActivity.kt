package com.pocketarcade

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.logging.CrashReporter
import com.pocketarcade.logging.ErrorLogger

/**
 * Hidden debug screen — accessible via 7-tap on the version number in Settings.
 *
 * Shows all locally buffered error log entries (newest first) and provides:
 *  - "Clear Logs" — wipes the local log file
 *  - "Push to Firebase" — manually triggers the offline sync
 *
 * This screen should never be promoted or made discoverable to regular users.
 */
class DebugLogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_log)

        ThemeManager.applyWindowBackground(this)

        findViewById<TextView>(R.id.btnDebugBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnClearLogs).setOnClickListener {
            ErrorLogger.clearAll(this)
            refresh()
            Toast.makeText(this, "Logs cleared.", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.btnPushNow).setOnClickListener {
            CrashReporter.syncLocalLogs(this)
            Toast.makeText(this,
                "Push triggered — check Crashlytics dashboard in a few minutes.",
                Toast.LENGTH_LONG).show()
        }

        refresh()
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun refresh() {
        val container = findViewById<LinearLayout>(R.id.logContainer)
        container.removeAllViews()

        val entries = ErrorLogger.getAll(this)
        findViewById<TextView>(R.id.tvLogCount).text = "${entries.length()} entries"

        if (entries.length() == 0) {
            container.addView(label("No log entries.", color = 0x88FFFFFF.toInt(), size = 12f).apply {
                setPadding(0, dp(24), 0, 0)
            })
            return
        }

        // Render newest first
        for (i in entries.length() - 1 downTo 0) {
            val obj    = entries.getJSONObject(i)
            val pushed = obj.optBoolean("pushed", false)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF111122.toInt())
                val pad = dp(10)
                setPadding(pad, pad, pad, pad)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(4)
                layoutParams = lp
            }

            val statusColor = if (pushed) 0xFF448844.toInt() else 0xFFCCAA00.toInt()
            card.addView(label(
                "${obj.optString("timestamp")}  [${if (pushed) "PUSHED" else "PENDING"}]",
                color = statusColor,
                size  = 9f
            ))
            card.addView(label(
                "${obj.optString("type")}: ${obj.optString("message")}",
                color = 0xFFFF6B6B.toInt(),
                size  = 11f
            ).apply { setPadding(0, dp(3), 0, 0) })
            card.addView(label(
                "Screen: ${obj.optString("screen")}  |  v${obj.optString("app_version")}",
                color = 0xFF888888.toInt(),
                size  = 9f
            ).apply { setPadding(0, dp(2), 0, 0) })

            val stack = obj.optString("stack")
            if (stack.isNotEmpty()) {
                card.addView(label(
                    stack.lines().take(5).joinToString("\n"),
                    color = 0xFF666688.toInt(),
                    size  = 8f
                ).apply { setPadding(0, dp(4), 0, 0) })
            }

            container.addView(card)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun label(text: String, color: Int, size: Float): TextView =
        TextView(this).apply {
            this.text = text
            textSize  = size
            setTextColor(color)
            typeface  = Typeface.MONOSPACE
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
