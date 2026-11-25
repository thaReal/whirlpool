package com.whirlpool.capture

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date

class UploadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val c = applicationContext
        val url = SongQueue.serverUrl(c)
        val token = SongQueue.token(c)
        val batch = SongQueue.snapshot(c)
        if (url.isEmpty() || token.isEmpty() || batch.length() == 0) return Result.success()

        val ids = (0 until batch.length()).map { batch.getJSONObject(it).getString("event_id") }.toSet()
        return try {
            val conn = (URL(url.trimEnd('/') + "/api/songs").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 8000; readTimeout = 15000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            conn.outputStream.use { it.write(JSONObject().put("songs", batch).toString().toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            val stamp = DateFormat.getDateTimeInstance().format(Date())
            when {
                code in 200..299 -> { SongQueue.remove(c, ids); SongQueue.setLastUpload(c, "OK $stamp"); Result.success() }
                code == 401 -> { SongQueue.setLastUpload(c, "Rejected (bad token) $stamp"); Result.failure() }
                else -> { SongQueue.setLastUpload(c, "HTTP $code $stamp"); Result.retry() }
            }
        } catch (e: IOException) {
            SongQueue.setLastUpload(c, "Server unreachable, will retry")
            Result.retry()  // e.g. away from home; stays queued
        }
    }
}
