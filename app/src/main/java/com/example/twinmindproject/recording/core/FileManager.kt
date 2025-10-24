//package com.example.twinmindproject.recording.core
//
//import android.content.Context
//import android.os.Environment
//import java.io.File
//import java.text.SimpleDateFormat
//import java.util.Locale
//import java.util.UUID
//
//class FileManager(private val ctx: Context) {
//    fun newSessionId(): String {
//        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
//        return "${ts}_${UUID.randomUUID().toString().substring(0, 8)}"
//    }
//
//    fun chunkFile(sessionId: String, index: Int): File {
//        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings/$sessionId")
//        dir.mkdirs()
//        return File(dir, "chunk_%03d.m4a".format(index))
//    }
//}

package com.example.twinmindproject.recording.core

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class FileManager(private val ctx: Context) {

    fun newSessionId(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())
        return "${ts}_${UUID.randomUUID().toString().substring(0, 8)}"
    }

    fun sessionDir(sessionId: String): File {
        val dir = File(
            ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            "recordings/$sessionId"
        )
        dir.mkdirs()
        return dir
    }

    fun chunkFile(sessionId: String, index: Int): File {
        return chunkFile(sessionId, index, ext = "m4a")
    }

    fun chunkFile(sessionId: String, index: Int, ext: String): File {
        val dir = sessionDir(sessionId)
        return File(dir, "chunk_%03d.%s".format(index, ext))
    }
}

