package com.example.twinmindproject.summary

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.twinmindproject.data.AppDb
import com.example.twinmindproject.data.SummaryEntity
import com.example.twinmindproject.data.SummaryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class SummaryRepository(private val context: Context) {
    private val db = AppDb.get(context)
    private val sDao = db.summaryDao()
    private val cDao = db.chunkDao()
    private val wm = WorkManager.getInstance(context)

    fun flow(sessionId: String): Flow<SummaryEntity?> = summaryFlowCompat(sessionId)

    fun flowAll(): Flow<List<SummaryEntity>> = sDao.flowAll()

    suspend fun ensureRow(sessionId: String) {
        sDao.upsert(
            SummaryEntity(
                sessionId = sessionId,
                status = SummaryStatus.PENDING
            )
        )
    }

    fun observeAllTranscribed(sessionId: String): Flow<Boolean> =
        cDao.notDoneCountFlow(sessionId).map { it == 0 }

    suspend fun allTranscribedNow(sessionId: String): Boolean =
        cDao.notDoneCountOnce(sessionId) == 0

    fun enqueue(sessionId: String) {
        val data = workDataOf(SummaryWorker.KEY_SESSION_ID to sessionId)
        val req: OneTimeWorkRequest = OneTimeWorkRequestBuilder<SummaryWorker>()
            .setInputData(data)
            .addTag("summary_generate")
            .addTag("session_$sessionId")
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        wm.enqueueUniqueWork("summary_$sessionId", ExistingWorkPolicy.REPLACE, req)
    }

    suspend fun enqueueWhenAllDone(sessionId: String) {
        if (allTranscribedNow(sessionId)) {
            enqueue(sessionId)
        }
    }

    fun retry(sessionId: String) = enqueue(sessionId)

    fun all() = sDao.all()

    private fun summaryFlowCompat(sessionId: String): Flow<SummaryEntity?> {
        return sDao.flowAll().map { list -> list.firstOrNull { it.sessionId == sessionId } }
    }
}

