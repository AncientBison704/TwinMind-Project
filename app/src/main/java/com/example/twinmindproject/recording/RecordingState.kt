package com.example.twinmindproject.recording

sealed class RecordingStatus {
    data object Idle : RecordingStatus()
    data class Recording(val elapsedSec: Int) : RecordingStatus()
    data class Paused(val elapsedSec: Int, val reason: String? = null) : RecordingStatus()
    data class Stopped(val filePath: String?) : RecordingStatus()
    data class Error(val message: String) : RecordingStatus()
}