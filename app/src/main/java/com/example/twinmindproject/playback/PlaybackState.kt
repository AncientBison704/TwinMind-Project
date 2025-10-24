package com.example.twinmindproject.playback

data class PlaybackState(
    val filePath: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val error: String? = null,
    val playlistIndex: Int = 0,
    val playlistSize: Int = 0
)
