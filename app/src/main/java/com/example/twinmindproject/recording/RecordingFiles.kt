package com.example.twinmindproject.recording

import android.content.Context
import android.os.Environment
import java.io.File

fun listRecordings(context: Context): List<File> {
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings")
    if (!dir.exists()) return emptyList()
    return dir.listFiles { f -> f.extension.equals("m4a", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

fun listLatestSessionChunks(context: Context): List<File> {
    val root = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings")
    if (!root.exists()) return emptyList()
    val sessionDirs = root.listFiles { f -> f.isDirectory }?.toList().orEmpty()
    if (sessionDirs.isEmpty()) return emptyList()

    val latestDir = sessionDirs.maxByOrNull { dir ->
        dir.listFiles()?.maxOfOrNull { it.lastModified() } ?: dir.lastModified()
    } ?: return emptyList()

    return latestDir.listFiles { f ->
        f.isFile && f.name.startsWith("chunk_") && f.extension.equals("m4a", true)
    }?.sortedBy { it.name } ?: emptyList()
}
