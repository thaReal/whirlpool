package com.whirlpool.capture

import android.content.Context
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Persistent on-phone queue (SharedPreferences) plus settings and upload scheduling. */
object SongQueue {
    private fun p(c: Context) = c.getSharedPreferences("whirlpool", Context.MODE_PRIVATE)

    fun serverUrl(c: Context) = p(c).getString("url", "") ?: ""
    fun token(c: Context) = p(c).getString("token", "") ?: ""
    fun saveConfig(c: Context, url: String, token: String) =
        p(c).edit().putString("url", url.trim()).putString("token", token.trim()).apply()

    @Synchronized fun snapshot(c: Context) = JSONArray(p(c).getString("queue", "[]"))
    fun size(c: Context) = snapshot(c).length()

    @Synchronized fun enqueue(c: Context, song: JSONObject) {
        p(c).edit().putString("queue", snapshot(c).put(song).toString()).apply()
    }

    @Synchronized fun remove(c: Context, sentIds: Set<String>) {
        val keep = JSONArray()
        val cur = snapshot(c)
        for (i in 0 until cur.length()) {
            val o = cur.getJSONObject(i)
            if (o.getString("event_id") !in sentIds) keep.put(o)
        }
        p(c).edit().putString("queue", keep.toString()).apply()
    }

    // Debug aids shown on the main screen
    fun lastCapture(c: Context) = p(c).getString("last_capture", "none yet") ?: ""
    fun setLastCapture(c: Context, s: String) = p(c).edit().putString("last_capture", s).apply()
    fun lastUpload(c: Context) = p(c).getString("last_upload", "none yet") ?: ""
    fun setLastUpload(c: Context, s: String) = p(c).edit().putString("last_upload", s).apply()

    /** Records package/channel names only (never notification content) of non-matching notifications. */
    @Synchronized fun noteSource(c: Context, source: String) {
        val list = (p(c).getString("sources", "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()
        if (source !in list) list.add(0, source)
        p(c).edit().putString("sources", list.take(15).joinToString("\n")).apply()
    }
    fun sources(c: Context) = p(c).getString("sources", "") ?: ""

    /** Upload runs only on an unmetered (Wi-Fi) network; WorkManager waits and retries with backoff. */
    fun scheduleUpload(c: Context) {
        val req = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("upload", ExistingWorkPolicy.KEEP, req)
    }
}
