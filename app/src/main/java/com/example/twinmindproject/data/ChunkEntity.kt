package com.example.twinmindproject.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chunks")
data class ChunkEntity(
    @PrimaryKey val key: String,
    val sessionId: String,
    val index: Int,
    val filePath: String,
    val status: ChunkStatus,
    val transcriptText: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
