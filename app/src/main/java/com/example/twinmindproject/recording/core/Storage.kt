package com.example.twinmindproject.recording.core

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

object Storage {
    fun freeBytes(context: Context): Long {
        val dir: File = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)!!
        val stat = StatFs(dir.absolutePath)
        return stat.availableBytes
    }
}
