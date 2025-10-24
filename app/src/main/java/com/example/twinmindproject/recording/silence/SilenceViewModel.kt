package com.example.twinmindproject.recording.silence

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class SilenceUi(
    val noAudio: Boolean = false
)

class SilenceViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = getApplication<Application>()
    private var service: SilenceService? = null
    private var collector: Job? = null

    private val _ui = MutableStateFlow(SilenceUi())
    val ui: StateFlow<SilenceUi> = _ui

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as SilenceService.LocalBinder).service()
            service = s
            collector?.cancel()
            collector = viewModelScope.launch {
                s.isSilent.collect { flag ->
                    _ui.update { it.copy(noAudio = flag) }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            collector?.cancel()
            _ui.update { it.copy(noAudio = false) }
        }
    }

    fun bind() {
        Intent(ctx, SilenceService::class.java).also {
            ctx.bindService(it, conn, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbind() {
        runCatching { ctx.unbindService(conn) }
        service = null
        collector?.cancel()
        _ui.update { it.copy(noAudio = false) }
    }

    fun start(outputFile: File) = service?.startRecording(outputFile)
    fun pause() = service?.pauseRecording()
    fun resume() = service?.resumeRecording()
    fun stop() = service?.stopRecording()
}
