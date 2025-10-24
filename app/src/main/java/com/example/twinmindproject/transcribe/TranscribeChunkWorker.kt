package com.example.twinmindproject.transcribe

import android.content.Context
import androidx.work.*
import com.example.twinmindproject.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import com.example.twinmindproject.BuildConfig

class TranscribeChunkWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val db = AppDb.get(appContext)
    private val dao = db.chunkDao()
    private val client = OkHttpClient()

    private fun log(msg: String) = android.util.Log.d("TranscribeWorker", msg)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return@withContext Result.failure()
        val index = inputData.getInt(KEY_INDEX, -1)
        val path = inputData.getString(KEY_PATH) ?: return@withContext Result.failure()
        val key = "${sessionId}_${index}"
        val file = File(path)

        log("START key=$key path=$path size=${file.length()}")

        if (!file.exists() || file.length() <= 44) {
            try { dao.setError(key, error = "File missing or too small: $path (${file.length()})") } catch (_: Exception) {}
            log("ABORT: missing/too small")
            return@withContext Result.failure()
        }

        try {
            dao.updateStatus(key, ChunkStatus.UPLOADING)
            log("DB: set UPLOADING")
        } catch (e: Exception) {
            log("DB ERROR (UPLOADING): ${e.javaClass.simpleName}: ${e.message}")
            return@withContext Result.failure()
        }

        android.util.Log.d("TranscribeWorker", "Key len=${BuildConfig.OPENAI_API_KEY.length}")
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isNullOrBlank()) {
            try { dao.setError(key, error = "Missing OpenAI API key") } catch (_: Exception) {}
            log("ABORT: missing API key")
            return@withContext Result.failure()
        }
        log("API key present")

        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart(
                "file", file.name,
                file.asRequestBody("audio/wav".toMediaType())
            )
            .build()

        val req = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        try {
            dao.updateStatus(key, ChunkStatus.TRANSCRIBING)
            client.newCall(req).execute().use { resp ->
                log("HTTP status=${resp.code}")

                if (!resp.isSuccessful) {
                    val bodySnippet = resp.body?.string()?.take(300)
                    log("HTTP error ${resp.code} body=$bodySnippet")

                    if (resp.code in 500..599 || resp.code == 429) {
                        return@withContext Result.retry()
                    } else {
                        dao.setError(key, error = "HTTP ${resp.code}: $bodySnippet")
                        return@withContext Result.failure()
                    }
                }

                val bodyText = resp.body!!.string()
                log("OK body len=${bodyText.length}")

                val json = JSONObject(bodyText)
                val text = json.optString("text", "")
                log("TRANSCRIPT len=${text.length}")

                dao.setTranscriptDone(key, text)
                return@withContext Result.success()
            }
        } catch (e: Exception) {
            log("EX ${e.javaClass.simpleName}: ${e.message}")
            return@withContext Result.retry()
        }
    }

    companion object {
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_INDEX = "index"
        const val KEY_PATH = "path"

        fun oneTime(sessionId: String, index: Int, path: String): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_SESSION_ID to sessionId,
                KEY_INDEX to index,
                KEY_PATH to path
            )
            return OneTimeWorkRequestBuilder<TranscribeChunkWorker>()
                .setInputData(data)
                .addTag("transcribe_chunk")
                .addTag("session_$sessionId")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        }
    }
}
