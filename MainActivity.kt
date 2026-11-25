package com.whirlpool.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import androidx.core.app.NotificationManagerCompat

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val url = EditText(this).apply { hint = "http://192.168.1.50:8787"; setText(SongQueue.serverUrl(context)); inputType = InputType.TYPE_TEXT_VARIATION_URI }
        val token = EditText(this).apply { hint = "API token"; setText(SongQueue.token(context)) }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply { text = label; setOnClickListener { onClick() } }
        status = TextView(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad)
            addView(url); addView(token)
            addView(button("Save and upload now") { SongQueue.saveConfig(context, url.text.toString(), token.text.toString()); SongQueue.scheduleUpload(context); refresh() })
            addView(button("Grant notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
            addView(status)
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private val context get() = this

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        status.text = "Notification access: ${if (enabled) "ON" else "OFF"}\n" +
            "Queued songs: ${SongQueue.size(this)}\n" +
            "Last capture: ${SongQueue.lastCapture(this)}\n" +
            "Last upload: ${SongQueue.lastUpload(this)}\n\n" +
            "Other notification sources seen (package / channel):\n${SongQueue.sources(this)}"
    }
}
