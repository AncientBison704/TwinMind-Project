package com.example.twinmindproject.playback

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


class PlaybackRepository(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val _state = MutableStateFlow(PlaybackState())
    private val mainHandler = Handler(Looper.getMainLooper())
    val state: StateFlow<PlaybackState> = _state

    private var tickerScope: CoroutineScope? = null

    private var playlist: List<String> = emptyList()
    private var index: Int = 0

    fun loadAndPlay(path: String) {
        releasePlayerOnly()
        playlist = listOf(path)
        index = 0
        loadAndPlayIndex(index)
    }

    fun loadAndPlayList(paths: List<String>) {
        if (paths.isEmpty()) return
        releasePlayerOnly()
        playlist = paths
        index = 0
        loadAndPlayIndex(index)
    }

    private fun loadAndPlayIndex(i: Int) {
        val path = playlist.getOrNull(i) ?: return
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener {
                    _state.value = _state.value.copy(
                        filePath = path,
                        isPlaying = true,
                        durationMs = it.duration,
                        positionMs = 0,
                        error = null,
                        playlistIndex = index,
                        playlistSize = playlist.size
                    )
                    start()
                    startTicker()
                }
                setOnCompletionListener { mp ->
                    stopTicker()
                    mp.setOnCompletionListener(null)
                    mp.release()
                    mediaPlayer = null

                    val next = index + 1
                    if (next < playlist.size) {
                        index = next
                        mainHandler.post { loadAndPlayIndex(index) }
                    } else {
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            positionMs = _state.value.durationMs,
                            playlistIndex = index,
                            playlistSize = playlist.size
                        )

                    }
                }
                setOnErrorListener { _, what, extra ->
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        error = "Playback error: $what/$extra"
                    )
                    stopTicker()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(isPlaying = false, error = e.message)
            stopTicker()
        }
    }


    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _state.value = _state.value.copy(isPlaying = false, positionMs = mp.currentPosition)
            stopTicker()
        } else {
            mp.start()
            _state.value = _state.value.copy(isPlaying = true)
            startTicker()
        }
    }

    fun seekTo(ms: Int) {
        val mp = mediaPlayer ?: return
        val clamped = ms.coerceIn(0, _state.value.durationMs)
        mp.seekTo(clamped)
        _state.value = _state.value.copy(positionMs = clamped)
    }

    fun stop() {
        mediaPlayer?.let {
            it.stop()
            it.reset()
        }
        _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
        stopTicker()
    }

    fun release() {
        stopTicker()
        releasePlayerOnly()
        playlist = emptyList()
        index = 0
        _state.value = PlaybackState()
    }

    private fun releasePlayerOnly() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startTicker() {
        stopTicker()
        tickerScope = CoroutineScope(Dispatchers.Default).apply {
            launch {
                while (isActive) {
                    delay(200)
                    mediaPlayer?.let { mp ->
                        if (mp.isPlaying) {
                            _state.value = _state.value.copy(positionMs = mp.currentPosition)
                        }
                    }
                }
            }
        }
    }

    private fun stopTicker() {
        tickerScope?.cancel()
        tickerScope = null
    }
}
