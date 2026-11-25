package com.whirlpool.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONObject
import java.util.UUID

class NowPlayingListener : NotificationListenerService() {
    companion object {
        private const val TAG = "WhirlpoolCapture"
        // UNVERIFIED guesses for the Pixel's Now Playing source; the main screen lists what was actually seen.
        private val PACKAGES = setOf("com.google.android.as", "com.google.intelligence.sense")
        private const val REPEAT_WINDOW_MS = 10 * 60 * 1000L
    }

    private var lastKey = ""
    private var lastAt = 0L

    override fun onListenerConnected() = SongQueue.scheduleUpload(this)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val channel = sbn.notification.channelId.orEmpty()
        val matches = sbn.packageName in PACKAGES || channel.contains("now_?playing".toRegex(RegexOption.IGNORE_CASE))
        if (!matches) {
            SongQueue.noteSource(this, "${sbn.packageName} / $channel")
            return
        }
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        Log.d(TAG, "candidate pkg=${sbn.packageName} channel=$channel title=$title text=$text")

        // Expected format: "Song Title by Artist" (split on the last " by ")
        val source = listOf(title, text).firstOrNull { " by " in it } ?: return
        val idx = source.lastIndexOf(" by ")
        val song = source.substring(0, idx).trim()
        val artist = source.substring(idx + 4).trim()
        if (song.isEmpty() || artist.isEmpty()) return

        val key = "$artist|$song".lowercase()
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < REPEAT_WINDOW_MS) return  // notification re-posted
        lastKey = key; lastAt = now

        SongQueue.enqueue(this, JSONObject()
            .put("event_id", UUID.randomUUID().toString())
            .put("title", song).put("artist", artist).put("detected_at_ms", now))
        SongQueue.setLastCapture(this, "$song - $artist")
        SongQueue.scheduleUpload(this)
    }
}
