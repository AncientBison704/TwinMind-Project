package com.example.twinmindproject.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChunkEntity)

    @Query("UPDATE chunks SET status=:status, updatedAt=:ts, error=NULL WHERE `key`=:key")
    suspend fun updateStatus(key: String, status: ChunkStatus, ts: Long = System.currentTimeMillis())

    @Query("UPDATE chunks SET status=:status, transcriptText=:text, updatedAt=:ts, error=NULL WHERE `key`=:key")
    suspend fun setTranscriptDone(key: String, text: String, status: ChunkStatus = ChunkStatus.DONE, ts: Long = System.currentTimeMillis())

    @Query("UPDATE chunks SET status=:status, error=:error, updatedAt=:ts WHERE `key`=:key")
    suspend fun setError(key: String, status: ChunkStatus = ChunkStatus.ERROR, error: String?, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM chunks WHERE sessionId=:sessionId ORDER BY `index` ASC")
    fun chunksForSession(sessionId: String): Flow<List<ChunkEntity>>

    @Query("SELECT * FROM chunks WHERE status IN ('QUEUED','ERROR') ORDER BY createdAt ASC")
    suspend fun pendingOrFailed(): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE `key`=:key LIMIT 1")
    suspend fun get(key: String): ChunkEntity?

    @Query("SELECT * FROM chunks WHERE sessionId = :sessionId ORDER BY `index` ASC")
    suspend fun chunksForSessionOnce(sessionId: String): List<ChunkEntity>

    @Query("""
    SELECT COUNT(*) FROM chunks 
    WHERE sessionId = :sessionId AND status != :doneStatus
""")
    fun notDoneCountFlow(sessionId: String, doneStatus: String = "DONE"): kotlinx.coroutines.flow.Flow<Int>

    @Query("""
    SELECT COUNT(*) FROM chunks 
    WHERE sessionId = :sessionId AND status != :doneStatus
""")
    suspend fun notDoneCountOnce(sessionId: String, doneStatus: String = "DONE"): Int

}
