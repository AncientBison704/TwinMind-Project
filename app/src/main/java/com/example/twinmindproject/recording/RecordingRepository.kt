package com.example.twinmindproject.recording

import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow

class RecordingRepository(private val context: Context) {

    private var service: RecordingService? = null
    private var conn: ServiceConnection? = null

    val status: StateFlow<RecordingStatus>?
        get() = service?.status

    val silence: StateFlow<Boolean>?
        get() = service?.isSilent

    val pauseReason: StateFlow<String?>?
        get() = service?.pauseReason

    fun bind(onReady: () -> Unit) {
        if (service != null) { onReady(); return }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as RecordingService.LocalBinder).service()
                onReady()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        conn = connection
        val intent = Intent(context, RecordingService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        conn?.let { runCatching { context.unbindService(it) } }
        conn = null
        service = null
    }


    fun startForegroundRecordingService() {
        val i = Intent(context, RecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, i)
        } else {
            context.startService(i)
        }
    }

    fun start()  = service?.startRecording()
    fun pause()  = service?.pauseRecording()
    fun resume() = service?.resumeRecording()
    fun stop()   = service?.stopRecording()
}

