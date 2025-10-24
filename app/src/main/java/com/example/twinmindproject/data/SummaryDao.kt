package com.example.twinmindproject.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE sessionId = :sid LIMIT 1")
    fun flowById(sid: String): Flow<SummaryEntity?>

    @Query("SELECT * FROM summaries ORDER BY updatedAt DESC")
    fun flowAll(): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries ORDER BY createdAt DESC")
    fun all(): Flow<List<SummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: SummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SummaryEntity)

    @Query("UPDATE summaries SET status = :st, updatedAt = :ts WHERE sessionId = :sid")
    suspend fun setStatus(sid: String, st: SummaryStatus, ts: Long = System.currentTimeMillis())

    @Query("UPDATE summaries SET draft = :draft, status = :st, updatedAt = :ts WHERE sessionId = :sid")
    suspend fun setDraft(sid: String, draft: String, st: SummaryStatus, ts: Long = System.currentTimeMillis())

    @Query("UPDATE summaries SET error = :err, status = :st, updatedAt = :ts WHERE sessionId = :sid")
    suspend fun setError(sid: String, err: String, st: SummaryStatus = SummaryStatus.ERROR, ts: Long = System.currentTimeMillis())

    @Query("""
        UPDATE summaries SET
          title = :title,
          summary = :summary,
          actionItems = :actionItems,
          keyPoints = :keyPoints,
          status = :st,
          updatedAt = :ts
        WHERE sessionId = :sid
    """)
    suspend fun setStructured(
        sid: String,
        st: SummaryStatus,
        title: String?,
        summary: String?,
        actionItems: String?,
        keyPoints: String?,
        ts: Long = System.currentTimeMillis()
    )
}
