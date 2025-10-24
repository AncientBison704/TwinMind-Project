package com.example.twinmindproject.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class PlaybackViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PlaybackRepository(app)
    val ui: StateFlow<PlaybackState> = repo.state

    fun play(path: String) = repo.loadAndPlay(path)
    fun playChunks(paths: List<String>) = repo.loadAndPlayList(paths)
    fun toggle() = repo.togglePlayPause()
    fun seekTo(ms: Int) = repo.seekTo(ms)
    fun stop() = repo.stop()
    override fun onCleared() { repo.release() }
}
