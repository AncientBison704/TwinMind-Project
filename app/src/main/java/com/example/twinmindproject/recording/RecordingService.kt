package com.example.twinmindproject.recording

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.example.twinmindproject.recording.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.twinmindproject.recording.core.OverlapPcmRecorderEngine
import com.example.twinmindproject.transcribe.TranscriptionRepository
import java.io.File

class RecordingService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.twinmindproject.recording.ACTION_STOP"
        const val ACTION_RESUME = "com.example.twinmindproject.recording.ACTION_RESUME"
        const val ACTION_PAUSE  = "com.example.twinmindproject.recording.ACTION_PAUSE"

        private const val CHUNK_SECONDS = 30
        private const val OVERLAP_MS = 2_000
    }

    inner class LocalBinder : Binder() { fun service(): RecordingService = this@RecordingService }
    private val binder = LocalBinder()

    private val MIN_START_SPACE = 10L * 1024 * 1024
    private val MIN_RUNTIME_SPACE = 5L * 1024 * 1024

    // State
    private val _status = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    val status: StateFlow<RecordingStatus> = _status
    private val _pauseReason = MutableStateFlow<String?>(null)
    val pauseReason: StateFlow<String?> = _pauseReason

    private var elapsedSec = 0
    private var pausedBySystem = false
    private var pausedByCall = false
    private var pausedReason: String? = null

    private var storageJob: Job? = null

    // Silence detection
    private val _isSilent = MutableStateFlow(false)
    val isSilent: StateFlow<Boolean> = _isSilent
    private var silenceJob: Job? = null
    private var silentAccumMs: Long = 0
    private val windowMs = 200L
    private val amplitudeThreshold = 1000
    private val requiredSilentMs = 10_000L
    private val minBytesPerWindow = 400

    // Controllers
    private lateinit var engine: OverlapPcmRecorderEngine
    private lateinit var files: FileManager
    private lateinit var focus: FocusController
    private lateinit var calls: CallController
    private val scope = CoroutineScope(Dispatchers.Default)

    // NEW: transcription repo + session bookkeeping
    private val transRepo by lazy { TranscriptionRepository(this) }
    private var currentSessionId: String? = null
    private var currentChunkIndex: Int = 0

    private val elapsedTicker = ElapsedTicker { sec ->
        elapsedSec = sec
        if (_status.value is RecordingStatus.Recording) {
            _status.value = RecordingStatus.Recording(elapsedSec)
            updateNotification()
        }
    }

    private val chunkTicker = ChunkTicker(CHUNK_SECONDS) {
        if (!hasFree(MIN_START_SPACE)) {
            stopForLowStorage()
        } else {
            try {
                val prevFile = engine.currentFile
                engine.startNewChunk()

                if (currentSessionId == null) {
                    currentSessionId = deriveSessionIdFromFile(engine.currentFile)
                }
                val prevIndex = currentChunkIndex
                currentChunkIndex += 1

                prevFile?.let { f ->
                    val sid = currentSessionId ?: deriveSessionIdFromFile(f) ?: "unknown"
                    val idx = parseIndexFromName(f.name) ?: prevIndex
                    scope.launch(Dispatchers.IO) {
                        transRepo.onChunkReady(sid, idx, f.absolutePath)
                    }
                }
            } catch (_: Exception) {
                stopForLowStorage()
            }
        }
    }

    private fun hasFree(minBytes: Long): Boolean =
        Storage.freeBytes(this) >= minBytes

    private fun startStorageMonitor() {
        stopStorageMonitor()
        storageJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                if (!hasFree(MIN_RUNTIME_SPACE)) {
                    withContext(Dispatchers.Main) { stopForLowStorage() }
                    break
                }
            }
        }
    }

    private fun stopStorageMonitor() {
        storageJob?.cancel()
        storageJob = null
    }

    private fun stopForLowStorage() {
        elapsedTicker.stop()
        chunkTicker.stop()
        stopStorageMonitor()
        stopSilenceMonitor()
        try { engine.finalize() } catch (_: Exception) {}
        focus.abandon()
        calls.unregister()
        engine.endSession(finalize = false)
        _status.value = RecordingStatus.Error("Recording stopped – Low storage")
        updateNotification(stopped = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        RecordingNotifications.ensureChannel(this)
        files = FileManager(this)

        engine = OverlapPcmRecorderEngine(
            fm = files,
            scope = scope,
            sampleRate = 44_100,
            overlapMs = OVERLAP_MS,
            chunkDurationMs = CHUNK_SECONDS * 1000
        )

        val am = getSystemService<AudioManager>()!!
        focus = FocusController(
            am,
            onLoss = {
                if (_status.value is RecordingStatus.Recording) {
                    pausedBySystem = true
                    if (!pausedByCall) pausedReason = "Paused – Audio focus lost"
                    pauseRecording()
                    _status.value = RecordingStatus.Paused(elapsedSec, pausedReason)
                    updateNotification()

                    scope.launch {
                        delay(1500)
                        if (pausedByCall && _status.value is RecordingStatus.Paused) {
                            _status.value = RecordingStatus.Paused(elapsedSec, "Paused – Phone call")
                            updateNotification()
                        }
                    }
                }
            },
            onGain = {
                if (!pausedByCall && _status.value is RecordingStatus.Paused) {
                    pausedBySystem = false
                    pausedReason = null
                    resumeRecording()
                } else {
                    pausedBySystem = false
                }
            }
        )

        val tm = getSystemService<TelephonyManager>()!!
        calls = CallController(
            tm,
            hasPhoneStatePermission = { Perms.hasPhoneState(this) },
            onCallActive = {
                if (_status.value is RecordingStatus.Recording) {
                    pausedByCall = true
                    pausedReason = "Paused – Phone call"
                    pauseRecording()
                    _status.value = RecordingStatus.Paused(elapsedSec, pausedReason)
                    updateNotification()
                }
            },
            onCallIdle = {
                if (pausedByCall && _status.value is RecordingStatus.Paused) {
                    pausedByCall = false
                    pausedReason = null
                    resumeRecording()
                }
            }
        )

    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP  -> stopRecording()
            ACTION_PAUSE -> {
                if (_status.value is RecordingStatus.Recording) {
                    pausedBySystem = false
                    pausedByCall = false
                    pausedReason = "Paused"
                    pauseRecording()
                    _status.value = RecordingStatus.Paused(elapsedSec, pausedReason)
                    updateNotification()
                }
            }

            ACTION_RESUME -> {
                val focusOk = focus.request()
                if (focusOk && !pausedByCall) {
                    pausedBySystem = false
                    pausedReason = null
                    resumeRecording()
                } else {
                    if (pausedByCall) {
                        pausedReason = "Paused – Phone call"
                    } else {
                        pausedReason = "Paused – Audio focus lost"
                    }
                    _status.value = RecordingStatus.Paused(elapsedSec)
                    updateNotification()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopInternal(finalize = false)
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (_status.value is RecordingStatus.Recording || _status.value is RecordingStatus.Paused) return

        startForeground(
            RecordingNotifications.NOTIF_ID,
            RecordingNotifications.build(this, "Starting…", "Setting up microphone")
        )

        if (!Perms.hasMic(this)) {
            NotificationManagerCompat.from(this).notify(
                RecordingNotifications.NOTIF_ID,
                RecordingNotifications.build(this, "Permission needed", "Microphone access is required")
            )
            _status.value = RecordingStatus.Error("Microphone permission not granted")
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }

        focus.request()
        calls.register()

        if (!hasFree(MIN_START_SPACE)) {
            NotificationManagerCompat.from(this).notify(
                RecordingNotifications.NOTIF_ID,
                RecordingNotifications.build(this, "Low storage", "Not enough space to start recording")
            )
            _status.value = RecordingStatus.Error("Not enough storage to start recording")
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }

        engine.beginSession()
        try {
            engine.startContinuousCapture()
            engine.startNewChunk()
        } catch (e: Exception) {
            NotificationManagerCompat.from(this).notify(
                RecordingNotifications.NOTIF_ID,
                RecordingNotifications.build(this, "Error", e.message ?: "Failed to start")
            )
            stopInternal(finalize = false)
            return
        }

        currentSessionId = deriveSessionIdFromFile(engine.currentFile)
        currentChunkIndex = 0

        elapsedSec = 0
        elapsedTicker.start(scope, 0)
        chunkTicker.start(scope)
        startStorageMonitor()
        startSilenceMonitor()

        _status.value = RecordingStatus.Recording(elapsedSec)
        updateNotification()
    }

    fun getCurrentChunkPaths(): List<String> = engine.chunks.map { it.absolutePath }

    fun pauseRecording() {
        engine.pause()
        elapsedTicker.stop()
        chunkTicker.stop()
        resetSilenceWindow()
        _status.value = RecordingStatus.Paused(elapsedSec, pausedReason)
        updateNotification()
    }

    fun resumeRecording() {
        engine.resume()
        elapsedTicker.start(scope, elapsedSec)
        chunkTicker.start(scope)
        resetSilenceWindow()
        pausedReason = null
        _status.value = RecordingStatus.Recording(elapsedSec)
        updateNotification()
    }


    fun stopRecording() {
        stopInternal(finalize = true)
    }

    private fun stopInternal(finalize: Boolean) {
        elapsedTicker.stop()
        chunkTicker.stop()
        stopStorageMonitor()
        stopSilenceMonitor()

        if (finalize) {
            engine.currentFile?.let { f ->
                val sid = currentSessionId ?: deriveSessionIdFromFile(f) ?: "unknown"
                val idx = parseIndexFromName(f.name) ?: currentChunkIndex
                scope.launch(Dispatchers.IO) {
                    transRepo.onChunkReady(sid, idx, f.absolutePath)
                }
            }
        }

        if (finalize) engine.finalize()
        focus.abandon()
        calls.unregister()
        pausedReason = null
        _pauseReason.value = null
        val path = if (finalize) engine.currentFile?.absolutePath else null
        engine.endSession(finalize = false)
        _status.value = RecordingStatus.Stopped(path)
        updateNotification(stopped = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(stopped: Boolean = false) {
        val s = _status.value
        val isRec = s is RecordingStatus.Recording
        val isPaused = s is RecordingStatus.Paused
        val sec = when (s) {
            is RecordingStatus.Recording -> s.elapsedSec
            is RecordingStatus.Paused    -> s.elapsedSec
            else -> 0
        }

        val (title, text) = when (s) {
            is RecordingStatus.Recording -> "Recording" to "Recording audio… ${formatElapsed(s.elapsedSec)}"
            is RecordingStatus.Paused    -> "Paused" to (pausedReason ?: "Paused… ${formatElapsed(s.elapsedSec)}")
            is RecordingStatus.Stopped   -> "Stopped" to (s.filePath ?: "Saved")
            is RecordingStatus.Error     -> "Error" to s.message
            RecordingStatus.Idle         -> "Idle" to ""
        }

        if (!stopped) {
            val notif = RecordingNotifications.buildLive(
                context     = this,
                title       = title,
                subtitle    = text,
                isRecording = isRec,
                elapsedSec  = sec,
                showResume  = isPaused
            )
            RecordingNotifications.notify(this, notif)
        }
    }

    private fun formatElapsed(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)

    interface ProvidesAmplitude { fun amplitude(): Int }

    private fun startSilenceMonitor() {
        stopSilenceMonitor()
        _isSilent.value = false
        silentAccumMs = 0

        val amplitudeProvider: (() -> Int)? =
            (engine as? ProvidesAmplitude)?.let { provider -> { provider.amplitude() } }

        var lastLen = engine.currentFile?.length() ?: 0L

        silenceJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(windowMs)
                if (_status.value is RecordingStatus.Paused) continue

                val silentNow: Boolean = if (amplitudeProvider != null) {
                    val amp = runCatching { amplitudeProvider.invoke() }.getOrDefault(0)
                    android.util.Log.d("Silence", "amp=$amp accum=$silentAccumMs isSilent=${_isSilent.value}")
                    amp < amplitudeThreshold
                } else {
                    val curLen = engine.currentFile?.length() ?: lastLen
                    val growth = (curLen - lastLen).coerceAtLeast(0L)
                    lastLen = curLen
                    growth < minBytesPerWindow
                }

                if (silentNow) {
                    silentAccumMs += windowMs
                    if (silentAccumMs >= requiredSilentMs && !_isSilent.value) _isSilent.value = true
                } else {
                    if (_isSilent.value) _isSilent.value = false
                    silentAccumMs = 0
                }
            }
        }
    }

    private fun stopSilenceMonitor() {
        silenceJob?.cancel()
        silenceJob = null
        _isSilent.value = false
        silentAccumMs = 0
    }

    private fun resetSilenceWindow() {
        silentAccumMs = 0
        _isSilent.value = false
    }

    private fun deriveSessionIdFromFile(f: File?): String? = f?.parentFile?.name

    private fun parseIndexFromName(name: String): Int? {
        val m = Regex("""chunk_(\d+)""").find(name) ?: return null
        return m.groupValues[1].toInt()
    }
}

