package com.whirlpool.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val title = TextView(this).apply { text = "Whirlpool Capture"; textSize = 22f; setPadding(0, 0, 0, pad) }
        val url = EditText(this).apply { hint = "http://192.168.1.50:8787"; setText(SongQueue.serverUrl(context)); inputType = InputType.TYPE_TEXT_VARIATION_URI }
        val token = EditText(this).apply { hint = "API token"; setText(SongQueue.token(context)) }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply { text = label; setOnClickListener { onClick() } }
        status = TextView(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad)
            addView(title); addView(url); addView(token)
            addView(button("Save and upload now") { SongQueue.saveConfig(context, url.text.toString(), token.text.toString()); SongQueue.scheduleUpload(context); refresh() })
            addView(button("Grant notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
            addView(status)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false; addView(layout) }
        // Android 15+ draws apps edge-to-edge, so keep content clear of the status bar, cutouts, nav bar and keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(scroll)
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
