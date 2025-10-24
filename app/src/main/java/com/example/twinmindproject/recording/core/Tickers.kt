package com.example.twinmindproject.recording.core

import kotlinx.coroutines.*

class ElapsedTicker(
    private val onTick: (Int) -> Unit
) {
    private var job: Job? = null
    private var sec = 0

    fun start(scope: CoroutineScope, initialSec: Int = 0) {
        stop()
        sec = initialSec
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                sec += 1
                onTick(sec)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}

class ChunkTicker(
    private val chunkSeconds: Int,
    private val onRotate: () -> Unit
) {
    private var job: Job? = null
    private var startMs: Long = 0

    fun start(scope: CoroutineScope) {
        stop()
        startMs = System.currentTimeMillis()
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val elapsed = ((System.currentTimeMillis() - startMs) / 1000).toInt()
                if (elapsed >= chunkSeconds) {
                    startMs = System.currentTimeMillis()
                    onRotate()
                }
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
