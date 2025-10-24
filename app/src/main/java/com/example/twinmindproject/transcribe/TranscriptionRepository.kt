package com.example.twinmindproject.transcribe

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.example.twinmindproject.data.*

class TranscriptionRepository(private val context: Context) {
    private val db = AppDb.get(context)
    private val dao = db.chunkDao()
    private val wm = WorkManager.getInstance(context)

    suspend fun onChunkReady(sessionId: String, index: Int, path: String) {
        val key = "${sessionId}_${index}"
        android.util.Log.d("TranscribeEnqueue", "onChunkReady key=$key path=$path")

        try {
            dao.upsert(
                ChunkEntity(
                    key = key,
                    sessionId = sessionId,
                    index = index,
                    filePath = path,
                    status = ChunkStatus.QUEUED
                )
            )
            android.util.Log.d("TranscribeEnqueue", "DB: upsert QUEUED ok")
        } catch (e: Exception) {
            android.util.Log.d("TranscribeEnqueue", "DB ERROR (UPSERT): ${e.javaClass.simpleName}: ${e.message}")
            return
        }

        enqueue(sessionId, index, path)
    }


    fun enqueue(sessionId: String, index: Int, path: String) {
        val req = TranscribeChunkWorker.oneTime(sessionId, index, path)
        android.util.Log.d("TranscribeEnqueue", "enqueue work sid=$sessionId idx=$index")
        WorkManager.getInstance(context)
            .enqueueUniqueWork("transcribe_${sessionId}_$index", ExistingWorkPolicy.KEEP, req)
    }

    suspend fun retryAll() {
        dao.pendingOrFailed().forEach { ent ->
            enqueue(ent.sessionId, ent.index, ent.filePath)
        }
    }

    fun transcriptFlow(sessionId: String) =
        dao.chunksForSession(sessionId)
}
