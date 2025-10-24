package com.example.twinmindproject.summary

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.twinmindproject.BuildConfig
import com.example.twinmindproject.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


class SummaryWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SESSION_ID = "sessionId"
        private const val TAG = "SummaryWorker"
    }

    private val db = AppDb.get(appContext)
    private val chunkDao = db.chunkDao()
    private val summaryDao = db.summaryDao()
    private val client = OkHttpClient.Builder().build()

    private fun log(msg: String) = Log.d(TAG, msg)

override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val sessionId = inputData.getString(KEY_SESSION_ID) ?: return@withContext Result.failure()

    val notDone = AppDb.get(applicationContext).chunkDao().notDoneCountOnce(sessionId)
    if (notDone > 0) {
        return@withContext Result.retry()
    }

    val chunks = AppDb.get(applicationContext).chunkDao().chunksForSessionOnce(sessionId) // DAO helper
    val transcript = buildString {
        chunks.sortedBy { it.index }.forEach { e ->
            if (!e.transcriptText.isNullOrBlank()) {
                append(e.transcriptText.trim())
                append("\n")
            }
        }
    }.trim()

    val summaryDao = AppDb.get(applicationContext).summaryDao()

    if (transcript.isBlank()) {
        summaryDao.setError(sessionId, "No transcript available")
        return@withContext Result.failure()
    }

    summaryDao.upsert(SummaryEntity(sessionId = sessionId, status = SummaryStatus.RUNNING))
    summaryDao.setDraft(sessionId, draft = "Generating summary…", st = SummaryStatus.RUNNING)

    val apiKey = BuildConfig.OPENAI_API_KEY
    if (apiKey.isNullOrBlank()) {
        summaryDao.setError(sessionId, "Missing OpenAI API key")
        return@withContext Result.failure()
    }

    val url = "https://api.openai.com/v1/chat/completions"
    val sys = """
        You are a helpful assistant that produces a structured JSON summary of a meeting transcript.
        Respond ONLY as JSON with fields:
        {
          "title": string,
          "summary": string,
          "action_items": [string],
          "key_points": [string]
        }
        Keep it concise and faithful to the transcript.
    """.trimIndent()
    val user = "Transcript:\n$transcript"

    val reqJson = JSONObject(
        mapOf(
            "model" to "gpt-4o-mini",
            "stream" to true,
            "temperature" to 0.2,
            "messages" to listOf(
                mapOf("role" to "system", "content" to sys),
                mapOf("role" to "user", "content" to user)
            )
        )
    )

    val reqBody = reqJson.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .post(reqBody)
        .build()

    try {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                log("HTTP ${resp.code} $errBody")
                if (resp.code == 429 || resp.code in 500..599) return@withContext Result.retry()
                summaryDao.setError(sessionId, "HTTP ${resp.code}: $errBody")
                return@withContext Result.failure()
            }

            val src = resp.body!!.source()
            val sb = StringBuilder()
            var lastEmit = 0L

            while (!src.exhausted()) {
                val line = src.readUtf8LineStrict()
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break

                try {
                    val obj = JSONObject(payload)
                    val arr = obj.optJSONArray("choices")
                    if (arr != null && arr.length() > 0) {
                        val delta = arr.getJSONObject(0).optJSONObject("delta")
                        val content = delta?.optString("content", null)
                        if (!content.isNullOrEmpty()) {
                            sb.append(content)
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 250) {
                                summaryDao.setDraft(sessionId, sb.toString(), SummaryStatus.RUNNING)
                                lastEmit = now
                            }
                        }
                    }
                } catch (_: Exception) {  }
            }

            val full = sb.toString().trim()
            if (full.isBlank()) {
                summaryDao.setError(sessionId, "Empty summary response")
                return@withContext Result.failure()
            }

            try {
                val json = JSONObject(full)
                val title = json.optString("title", "")
                val summary = json.optString("summary", "")
                val actionItems = json.optJSONArray("action_items")
                    ?.let { (0 until it.length()).joinToString("\n") { idx -> it.getString(idx) } }
                    ?: ""
                val keyPoints = json.optJSONArray("key_points")
                    ?.let { (0 until it.length()).joinToString("\n") { idx -> it.getString(idx) } }
                    ?: ""

                summaryDao.setStructured(
                    sid = sessionId,
                    st = SummaryStatus.DONE,
                    title = title.ifBlank { null },
                    summary = summary.ifBlank { null },
                    actionItems = actionItems.ifBlank { null },
                    keyPoints = keyPoints.ifBlank { null }
                )
                return@withContext Result.success()

            } catch (e: Exception) {
                summaryDao.setError(sessionId, "Bad JSON from model. Please Retry.")
                summaryDao.setDraft(sessionId, full, SummaryStatus.ERROR)
                return@withContext Result.failure()
            }
        }
    } catch (e: Exception) {
        log("EX ${e.javaClass.simpleName}: ${e.message}")
        return@withContext Result.retry()
    }
}

}
