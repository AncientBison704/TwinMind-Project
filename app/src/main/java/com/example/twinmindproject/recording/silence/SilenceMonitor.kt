package com.example.twinmindproject.recording.silence

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SilenceMonitor(
    private val amplitudeProvider: () -> Int,
    private val isPausedProvider: () -> Boolean = { false },
    private val windowMs: Long = 200L,
    private val silenceThreshold: Int = 10000,
    private val requiredSilentMs: Long = 1_000L
) {
    private val _isSilent = MutableStateFlow(false)
    val isSilent: StateFlow<Boolean> = _isSilent

    private var job: Job? = null
    private var silentAccumMs = 0L

    fun start(scope: CoroutineScope) {
        stop()
        _isSilent.value = false
        silentAccumMs = 0L
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(windowMs)
                if (isPausedProvider()) continue
                val amp = runCatching { amplitudeProvider() }.getOrElse { 0 }
                if (amp < silenceThreshold) {
                    silentAccumMs += windowMs
                    if (silentAccumMs >= requiredSilentMs && !_isSilent.value) {
                        _isSilent.value = true
                    }
                } else {
                    if (_isSilent.value) _isSilent.value = false
                    silentAccumMs = 0L
                }
            }
        }
    }

    fun resetWindow() {
        silentAccumMs = 0L
        _isSilent.value = false
    }

    fun stop() {
        job?.cancel()
        job = null
        _isSilent.value = false
        silentAccumMs = 0L
    }
}
