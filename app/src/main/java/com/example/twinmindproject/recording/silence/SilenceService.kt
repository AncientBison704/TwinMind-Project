package com.example.twinmindproject.recording.silence

import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import java.io.File


class SilenceService : Service() {

    inner class LocalBinder : Binder() {
        fun service(): SilenceService = this@SilenceService
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate)

    private var recorder: MediaRecorder? = null
    private var isPaused: Boolean = false

    private val monitor = SilenceMonitor(
        amplitudeProvider = { recorder?.maxAmplitude ?: 0 },
        isPausedProvider = { isPaused },
        windowMs = 200L,
        silenceThreshold = 1000,
        requiredSilentMs = 10_000L
    )
    val isSilent: StateFlow<Boolean> = monitor.isSilent

    fun startRecording(outputFile: File) {
        stopRecording()
        recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        isPaused = false
        monitor.start(serviceScope)
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= 24 && recorder != null && !isPaused) {
            recorder?.pause()
            isPaused = true
            monitor.resetWindow()
        }
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= 24 && recorder != null && isPaused) {
            recorder?.resume()
            isPaused = false
            monitor.resetWindow()
        }
    }

    fun stopRecording() {
        runCatching { recorder?.stop() }
        recorder?.reset()
        recorder?.release()
        recorder = null
        isPaused = false
        monitor.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        serviceScope.cancel()
    }
}
