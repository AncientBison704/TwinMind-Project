//package com.example.twinmindproject.recording.core
//
//import android.media.*
//import kotlinx.coroutines.*
//import java.io.*
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import kotlin.math.abs
//import kotlin.math.min
//import com.example.twinmindproject.recording.RecordingService
//
//class OverlapPcmRecorderEngine(
//    private val fm: FileManager,
//    private val scope: CoroutineScope,
//    private val sampleRate: Int = 44_100,
//    private val overlapMs: Int = 2_000,
//    private val chunkDurationMs: Int = 30_000
//) : RecordingService.ProvidesAmplitude {
//
//    val chunks: MutableList<File> = mutableListOf()
//    var currentFile: File? = null
//        private set
//
//    private var sessionId: String? = null
//    private var chunkIndex = 0
//
//    private var ar: AudioRecord? = null
//    private var readJob: Job? = null
//
//    private var fos: FileOutputStream? = null
//    private var bytesRemainingThisChunk: Long = 0
//
//    private val bytesPerSample = 2
//    private val channels = 1
//
//    private val overlapBytes = overlapMs * sampleRate / 1000 * bytesPerSample * channels
//    private val ring = ByteArray(maxOf(overlapBytes, 1))
//    private var ringWritePos = 0
//    private var ringFilled = 0
//
//    @Volatile private var lastPeak: Int = 0
//
//    fun beginSession(): String {
//        sessionId = fm.newSessionId()
//        chunkIndex = 0
//        chunks.clear()
//        currentFile = null
//        return sessionId!!
//    }
//
//    fun endSession(finalize: Boolean) {
//        if (finalize) finalize()
//        closeAudioRecord()
//        closeWriter()
//        sessionId = null
//        chunkIndex = 0
//        currentFile = null
//    }
//
//
//    fun startContinuousCapture() {
//        if (ar != null) return
//        val minBuf = AudioRecord.getMinBufferSize(
//            sampleRate,
//            AudioFormat.CHANNEL_IN_MONO,
//            AudioFormat.ENCODING_PCM_16BIT
//        )
//        val bufSize = maxOf(minBuf, sampleRate * bytesPerSample)
//        ar = AudioRecord(
//            MediaRecorder.AudioSource.MIC,
//            sampleRate,
//            AudioFormat.CHANNEL_IN_MONO,
//            AudioFormat.ENCODING_PCM_16BIT,
//            bufSize
//        )
//        ar?.startRecording()
//
//        // Continuous read loop
//        readJob = scope.launch(Dispatchers.Default) {
//            val readBuf = ByteArray(4096)
//            while (isActive && ar?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
//                val n = ar?.read(readBuf, 0, readBuf.size) ?: 0
//                if (n > 0) {
//                    lastPeak = computePeak16(readBuf, n)
//
//                    pushIntoRing(readBuf, n)
//
//                    writePcmToChunk(readBuf, n)
//                }
//            }
//        }
//    }
//
//    fun startNewChunk() {
//        closeWriter()
//
//        val sid = requireNotNull(sessionId) { "Session not started" }
//        val file = fm.chunkFile(sid, chunkIndex).let { ensureWavExt(it) }
//        currentFile = file
//        chunks += file
//        chunkIndex++
//
//        fos = FileOutputStream(file).also {
//            writeWavHeader(it, sampleRate, channels, 16, 0)
//        }
//
//        bytesRemainingThisChunk = ((overlapMs + chunkDurationMs).toLong()
//                * sampleRate / 1000 * bytesPerSample * channels)
//
//        dumpRingToWriter()
//    }
//
//    fun pause() {
//        closeAudioRecord()
//    }
//
//    fun resume() {
//        // resume continuous capture
//        startContinuousCapture()
//    }
//
//    fun finalize() {
//        // Finish current chunk and stop capture
//        closeWriter()
//        closeAudioRecord()
//    }
//
//    fun rotateIfNeeded() {
//        startNewChunk()
//    }
//
//    override fun amplitude(): Int = lastPeak
//
//    private fun computePeak16(buf: ByteArray, len: Int): Int {
//        var peak = 0
//        var i = 0
//        while (i + 1 < len) {
//            val lo = buf[i].toInt() and 0xFF
//            val hi = buf[i + 1].toInt()
//            val s = (hi shl 8) or lo
//            val absVal = abs((s.toShort()).toInt())
//            if (absVal > peak) peak = absVal
//            i += 2
//        }
//        return peak
//    }
//
//    private fun pushIntoRing(src: ByteArray, len: Int) {
//        var off = 0
//        var remain = len
//        while (remain > 0) {
//            val space = ring.size - ringWritePos
//            val toCopy = min(space, remain)
//            System.arraycopy(src, off, ring, ringWritePos, toCopy)
//            ringWritePos = (ringWritePos + toCopy) % ring.size
//            ringFilled = min(ringFilled + toCopy, ring.size)
//            off += toCopy
//            remain -= toCopy
//        }
//    }
//
//    private fun dumpRingToWriter() {
//        val out = fos ?: return
//        if (ringFilled == 0) return
//        val start = (ringWritePos - ringFilled + ring.size) % ring.size
//        var remaining = min(ringFilled, overlapBytes)
//        var pos = start
//        val tmp = ByteArray(4096)
//        while (remaining > 0) {
//            val copy = min(remaining, min(tmp.size, ring.size - pos))
//            System.arraycopy(ring, pos, tmp, 0, copy)
//            out.write(tmp, 0, copy)
//            bytesRemainingThisChunk -= copy
//            pos = (pos + copy) % ring.size
//            remaining -= copy
//        }
//    }
//
//    private fun writePcmToChunk(buf: ByteArray, len: Int) {
//        val out = fos ?: return
//        if (bytesRemainingThisChunk <= 0) return
//        val toWrite = min(len.toLong(), bytesRemainingThisChunk).toInt()
//        if (toWrite > 0) {
//            out.write(buf, 0, toWrite)
//            bytesRemainingThisChunk -= toWrite
//        }
//        if (bytesRemainingThisChunk <= 0) {
//            closeWriter()
//        }
//    }
//
//    private fun closeAudioRecord() {
//        readJob?.cancel(); readJob = null
//        ar?.apply { runCatching { stop() }; release() }
//        ar = null
//    }
//
//    private fun closeWriter() {
//        fos?.let { out ->
//            val totalSize = out.channel.size()
//            patchWavSizes(currentFile!!, totalSize)
//            runCatching { out.flush() }
//            runCatching { out.close() }
//        }
//        fos = null
//        bytesRemainingThisChunk = 0
//    }
//
//    private fun writeWavHeader(out: FileOutputStream, sr: Int, ch: Int, bitsPerSample: Int, dataLen: Int) {
//        val byteRate = sr * ch * bitsPerSample / 8
//        val blockAlign = ch * bitsPerSample / 8
//        val totalLen = 36 + dataLen
//
//        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
//        buf.put("RIFF".toByteArray())
//        buf.putInt(totalLen)
//        buf.put("WAVE".toByteArray())
//        buf.put("fmt ".toByteArray())
//        buf.putInt(16)
//        buf.putShort(1) // PCM
//        buf.putShort(ch.toShort())
//        buf.putInt(sr)
//        buf.putInt(byteRate)
//        buf.putShort(blockAlign.toShort())
//        buf.putShort(bitsPerSample.toShort())
//        buf.put("data".toByteArray())
//        buf.putInt(dataLen)
//        out.write(buf.array(), 0, 44)
//    }
//
//    private fun patchWavSizes(file: File, totalSize: Long) {
//        // Patch ChunkSize @4, Subchunk2Size @40
//        RandomAccessFile(file, "rw").use { raf ->
//            val dataSize = (totalSize - 44).coerceAtLeast(0)
//            raf.seek(4);  raf.writeIntLE((36 + dataSize).toInt())
//            raf.seek(40); raf.writeIntLE(dataSize.toInt())
//        }
//    }
//
//    private fun RandomAccessFile.writeIntLE(v: Int) {
//        write(byteArrayOf(
//            (v and 0xFF).toByte(),
//            ((v shr 8) and 0xFF).toByte(),
//            ((v shr 16) and 0xFF).toByte(),
//            ((v shr 24) and 0xFF).toByte()
//        ))
//    }
//
//    private fun ensureWavExt(file: File): File {
//        if (file.extension.lowercase() == "wav") return file
//        val wav = File(file.parentFile, file.nameWithoutExtension + ".wav")
//        if (file.exists()) file.renameTo(wav)
//        return wav
//    }
//}

package com.example.twinmindproject.recording.core

import android.annotation.SuppressLint
import android.media.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import com.example.twinmindproject.recording.RecordingService

class OverlapPcmRecorderEngine(
    private val fm: FileManager,
    private val scope: CoroutineScope,
    private val sampleRate: Int = 44_100,
    private val overlapMs: Int = 2_000,
    private val chunkDurationMs: Int = 30_000
) : RecordingService.ProvidesAmplitude {

    val chunks: MutableList<File> = mutableListOf()
    var currentFile: File? = null
        private set

    private var sessionId: String? = null
    private var chunkIndex = 0

    private var ar: AudioRecord? = null
    private var readJob: Job? = null

    private var fos: FileOutputStream? = null
    private var bytesRemainingThisChunk: Long = 0

    private val bytesPerSample = 2
    private val channels = 1

    private val overlapBytes = overlapMs * sampleRate / 1000 * bytesPerSample * channels
    private val ring = ByteArray(maxOf(overlapBytes, 1))
    private var ringWritePos = 0
    private var ringFilled = 0

    @Volatile private var lastPeak: Int = 0

    private fun log(msg: String) = android.util.Log.d("OverlapPcmEngine", msg)

    fun beginSession(): String {
        sessionId = fm.newSessionId()
        chunkIndex = 0
        chunks.clear()
        currentFile = null
        return sessionId!!
    }

    fun endSession(finalize: Boolean) {
        if (finalize) finalize()
        closeAudioRecord()
        closeWriter()
        sessionId = null
        chunkIndex = 0
        currentFile = null
    }

    @SuppressLint("MissingPermission")
    fun startContinuousCapture() {
        if (ar != null) return

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, sampleRate * bytesPerSample)

        try {
            ar = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
        } catch (se: SecurityException) {
            log("SecurityException creating AudioRecord: ${se.message}")
            ar = null
            return
        } catch (e: Exception) {
            log("Exception creating AudioRecord: ${e.message}")
            ar = null
            return
        }

        try {
            ar?.startRecording()
        } catch (se: SecurityException) {
            log("SecurityException starting recording: ${se.message}")
            runCatching { ar?.release() }
            ar = null
            return
        } catch (e: Exception) {
            log("Exception starting recording: ${e.message}")
            runCatching { ar?.release() }
            ar = null
            return
        }

        readJob = scope.launch(Dispatchers.Default) {
            val readBuf = ByteArray(4096)
            while (isActive && ar?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val n = try {
                    ar?.read(readBuf, 0, readBuf.size) ?: 0
                } catch (e: Exception) {
                    log("AudioRecord.read exception: ${e.message}")
                    0
                }
                if (n > 0) {
                    lastPeak = computePeak16(readBuf, n)
                    pushIntoRing(readBuf, n)
                    writePcmToChunk(readBuf, n)
                }
            }
        }
    }

    fun startNewChunk() {
        closeWriter()

        val sid = requireNotNull(sessionId) { "Session not started" }
        val file = fm.chunkFile(sid, chunkIndex).let { ensureWavExt(it) }
        currentFile = file
        chunks += file
        chunkIndex++

        fos = FileOutputStream(file).also {
            writeWavHeader(it, sampleRate, channels, 16, 0)
        }

        bytesRemainingThisChunk = ((overlapMs + chunkDurationMs).toLong()
                * sampleRate / 1000 * bytesPerSample * channels)

        dumpRingToWriter()
    }

    fun pause() {
        closeAudioRecord()
    }

    fun resume() {
        startContinuousCapture()
    }

    fun finalize() {
        closeWriter()
        closeAudioRecord()
    }

    fun rotateIfNeeded() {
        startNewChunk()
    }

    override fun amplitude(): Int = lastPeak

    private fun computePeak16(buf: ByteArray, len: Int): Int {
        var peak = 0
        var i = 0
        while (i + 1 < len) {
            val lo = buf[i].toInt() and 0xFF
            val hi = buf[i + 1].toInt()
            val s = (hi shl 8) or lo
            val absVal = abs((s.toShort()).toInt())
            if (absVal > peak) peak = absVal
            i += 2
        }
        return peak
    }

    private fun pushIntoRing(src: ByteArray, len: Int) {
        var off = 0
        var remain = len
        while (remain > 0) {
            val space = ring.size - ringWritePos
            val toCopy = min(space, remain)
            System.arraycopy(src, off, ring, ringWritePos, toCopy)
            ringWritePos = (ringWritePos + toCopy) % ring.size
            ringFilled = min(ringFilled + toCopy, ring.size)
            off += toCopy
            remain -= toCopy
        }
    }

    private fun dumpRingToWriter() {
        val out = fos ?: return
        if (ringFilled == 0) return
        val start = (ringWritePos - ringFilled + ring.size) % ring.size
        var remaining = min(ringFilled, overlapBytes)
        var pos = start
        val tmp = ByteArray(4096)
        while (remaining > 0) {
            val copy = min(remaining, min(tmp.size, ring.size - pos))
            System.arraycopy(ring, pos, tmp, 0, copy)
            out.write(tmp, 0, copy)
            bytesRemainingThisChunk -= copy
            pos = (pos + copy) % ring.size
            remaining -= copy
        }
    }

    private fun writePcmToChunk(buf: ByteArray, len: Int) {
        val out = fos ?: return
        if (bytesRemainingThisChunk <= 0) return
        val toWrite = min(len.toLong(), bytesRemainingThisChunk).toInt()
        if (toWrite > 0) {
            out.write(buf, 0, toWrite)
            bytesRemainingThisChunk -= toWrite
        }
        if (bytesRemainingThisChunk <= 0) {
            closeWriter()
        }
    }

    private fun closeAudioRecord() {
        readJob?.cancel(); readJob = null
        ar?.apply {
            runCatching { stop() }
            runCatching { release() }
        }
        ar = null
    }

    private fun closeWriter() {
        fos?.let { out ->
            val totalSize = out.channel.size()
            patchWavSizes(currentFile!!, totalSize)
            runCatching { out.flush() }
            runCatching { out.close() }
        }
        fos = null
        bytesRemainingThisChunk = 0
    }

    private fun writeWavHeader(out: FileOutputStream, sr: Int, ch: Int, bitsPerSample: Int, dataLen: Int) {
        val byteRate = sr * ch * bitsPerSample / 8
        val blockAlign = ch * bitsPerSample / 8
        val totalLen = 36 + dataLen

        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(totalLen)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(ch.toShort())
        buf.putInt(sr)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray())
        buf.putInt(dataLen)
        out.write(buf.array(), 0, 44)
    }

    private fun patchWavSizes(file: File, totalSize: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            val dataSize = (totalSize - 44).coerceAtLeast(0)
            raf.seek(4);  raf.writeIntLE((36 + dataSize).toInt())
            raf.seek(40); raf.writeIntLE(dataSize.toInt())
        }
    }

    private fun RandomAccessFile.writeIntLE(v: Int) {
        write(byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte()
        ))
    }

    private fun ensureWavExt(file: File): File {
        if (file.extension.lowercase() == "wav") return file
        val wav = File(file.parentFile, file.nameWithoutExtension + ".wav")
        if (file.exists()) file.renameTo(wav)
        return wav
    }
}

