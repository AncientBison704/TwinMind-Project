package com.example.twinmindproject.data

import androidx.room.*

enum class SummaryStatus { PENDING, RUNNING, DONE, ERROR }

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey val sessionId: String,
    val status: SummaryStatus = SummaryStatus.PENDING,
    val draft: String = "",
    val title: String? = null,
    val summary: String? = null,
    val actionItems: String? = null,
    val keyPoints: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)