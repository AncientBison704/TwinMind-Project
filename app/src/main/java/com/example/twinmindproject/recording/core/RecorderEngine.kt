package com.example.twinmindproject.recording.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.example.twinmindproject.recording.RecordingService
import java.io.File

class RecorderEngine(
    private val ctx: Context,
    private val fm: FileManager
) : RecordingService.ProvidesAmplitude {

    private var recorder: MediaRecorder? = null
    private var sessionId: String? = null
    private var chunkIndex = 0

    val chunks: MutableList<File> = mutableListOf()
    var currentFile: File? = null
        private set

    fun beginSession(): String {
        sessionId = fm.newSessionId()
        chunkIndex = 0
        chunks.clear()
        return sessionId!!
    }

    fun endSession(finalize: Boolean) {
        if (finalize) finalize()
        recorder = null
        currentFile = null
        sessionId = null
        chunkIndex = 0
    }

    @SuppressLint("MissingPermission")
    fun startNewChunk() {
        finalize()

        val sid = requireNotNull(sessionId) { "Session not started" }
        currentFile = fm.chunkFile(sid, chunkIndex)

        recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        recorder!!.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(48_000)
            setAudioEncodingBitRate(128_000)
            setOutputFile(currentFile!!.absolutePath)
            prepare()
            start()
        }

        chunks.add(currentFile!!)
        chunkIndex++
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { recorder?.pause() }
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { recorder?.resume() }
        }
    }

    fun finalize() {
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) {  }
        recorder = null
    }

    override fun amplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (_: Exception) {
        0
    }
}
