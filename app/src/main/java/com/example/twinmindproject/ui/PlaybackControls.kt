//package com.example.twinmindproject.ui
//
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.Button
//import androidx.compose.material3.OutlinedButton
//import androidx.compose.material3.Slider
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import com.example.twinmindproject.playback.PlaybackState
//import com.example.twinmindproject.playback.PlaybackViewModel
//import java.io.File
//import kotlin.math.max
//
//@Composable
//fun PlaybackControls(
//    vm: PlaybackViewModel,
//    latestFile: File?
//) {
//    val state by vm.ui.collectAsState()
//
//    Text("Playback", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
//    if (latestFile == null) {
//        Text("No recordings yet.")
//        return
//    }
//    Text("Latest: ${latestFile.name}")
//
//    val duration = max(state.durationMs, 1)
//    var sliderPos by remember(state.filePath, state.positionMs) {
//        mutableStateOf(state.positionMs.toFloat())
//    }
//
//    Spacer(Modifier.height(8.dp))
//    Slider(
//        value = sliderPos,
//        onValueChange = { sliderPos = it },
//        onValueChangeFinished = { vm.seekTo(sliderPos.toInt()) },
//        valueRange = 0f..duration.toFloat(),
//        modifier = Modifier.fillMaxWidth()
//    )
//    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//        Button(onClick = {
//            // If player is not loaded for this file, start it; otherwise toggle
//            if (state.filePath != latestFile.absolutePath) {
//                vm.play(latestFile.absolutePath)
//            } else {
//                vm.toggle()
//            }
//        }) {
//            Text(if (state.isPlaying && state.filePath == latestFile.absolutePath) "Pause" else "Play")
//        }
//        OutlinedButton(onClick = { vm.stop() }) { Text("Stop") }
//    }
//    Text("${formatMs(state.positionMs)} / ${formatMs(state.durationMs)}")
//}
//
//private fun formatMs(ms: Int): String {
//    val totalSec = ms / 1000
//    val m = totalSec / 60
//    val s = totalSec % 60
//    return "%02d:%02d".format(m, s)
//}

package com.example.twinmindproject.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.twinmindproject.playback.PlaybackViewModel
import java.io.File
import kotlin.math.max

@Composable
fun PlaybackControls(
    vm: PlaybackViewModel,
    latestSessionDir: File?
) {
    val state by vm.ui.collectAsState()

    Text("Playback", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))

    if (latestSessionDir == null || !latestSessionDir.exists()) {
        Text("No recordings yet.")
        return
    }
    Text("Session: ${latestSessionDir.name}")

//    val chunkFiles by remember(latestSessionDir) {
//        mutableStateOf(
//            latestSessionDir.listFiles { f ->
//                f.isFile && f.name.startsWith("chunk_") && f.name.endsWith(".m4a", ignoreCase = true)
//            }?.sortedBy { it.name } ?: emptyList()
//        )
//    }

    val chunkFiles = latestSessionDir.listFiles { f ->
        f.isFile &&
                f.name.startsWith("chunk_") &&
                (f.name.endsWith(".wav", ignoreCase = true) || f.name.endsWith(".m4a", ignoreCase = true)) &&
                f.length() > 1_024
    }?.sortedBy { it.name } ?: emptyList()



    if (chunkFiles.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("No chunks found in this session.")
        return
    }

    val playingThisSession = state.filePath?.let { File(it).parentFile == latestSessionDir } == true

    val duration = max(state.durationMs, 1)
    var sliderPos by remember(state.filePath, state.positionMs) {
        mutableStateOf(state.positionMs.toFloat())
    }

    Spacer(Modifier.height(8.dp))
    Slider(
        value = sliderPos,
        onValueChange = { sliderPos = it },
        onValueChangeFinished = { vm.seekTo(sliderPos.toInt()) },
        valueRange = 0f..duration.toFloat(),
        modifier = Modifier.fillMaxWidth()
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
            if (!playingThisSession) {
                vm.playChunks(chunkFiles.map { it.absolutePath })
            } else {
                vm.toggle()
            }
        }) {
            Text(if (state.isPlaying && playingThisSession) "Pause" else "Play")
        }
        OutlinedButton(onClick = { vm.stop() }) { Text("Stop") }
    }

    val chunkInfo = if (state.playlistSize > 1) {
        "  (chunk ${state.playlistIndex + 1}/${state.playlistSize})"
    } else ""

    Text("${formatMs(state.positionMs)} / ${formatMs(state.durationMs)}$chunkInfo")
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}


