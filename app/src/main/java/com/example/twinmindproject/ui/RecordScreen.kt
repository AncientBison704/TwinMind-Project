package com.example.twinmindproject.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.twinmindproject.recording.*
import com.example.twinmindproject.transcribe.TranscriptViewModel
import com.example.twinmindproject.ui.SummaryHistoryList
import com.example.twinmindproject.summary.SummaryListViewModel
import java.io.File

@Composable
fun RecordScreen(
    recordVm: RecordingViewModel,
) {
    val ui by recordVm.ui.collectAsState()
    val status = ui.status
    val noAudioFlag = status is RecordingStatus.Recording && ui.noAudio
    val summaryListVm: SummaryListViewModel = viewModel()
    val allSummaries by summaryListVm.items.collectAsState(initial = emptyList())

    val neededPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(Manifest.permission.READ_PHONE_STATE)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var requestedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {  }

    LaunchedEffect(Unit) {
        recordVm.bind()
        if (!requestedOnce) {
            requestedOnce = true
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }
    DisposableEffect(Unit) { onDispose { recordVm.unbind() } }

    val timer = when (val s = status) {
        is RecordingStatus.Recording -> formatSec(s.elapsedSec)
        is RecordingStatus.Paused -> formatSec(s.elapsedSec)
        else -> "00:00"
    }


    val pausedReason = (status as? RecordingStatus.Paused)?.reason.orEmpty()
    val statusText = when (status) {
        is RecordingStatus.Recording -> "Recording..."
        is RecordingStatus.Paused    -> when {
            pausedReason.contains("phone call", ignoreCase = true) -> "Paused - Phone call"
            pausedReason.contains("audio focus", ignoreCase = true) -> "Paused - Audio focus lost"
            else -> "Paused"
        }
        is RecordingStatus.Stopped   -> "Stopped"
        is RecordingStatus.Error     -> status.message
        RecordingStatus.Idle         -> "Idle"
    }



    val primaryBtnLabel = when (status) {
        is RecordingStatus.Recording, is RecordingStatus.Paused -> "Stop"
        else -> "Record"
    }

    val context = LocalContext.current
    val latestSessionDir: File? by remember(status) {
        mutableStateOf(
            when (status) {
                is RecordingStatus.Stopped -> {
                    val p = status.filePath
                    val f = p?.let { File(it) }
                    f?.parentFile ?: listRecordings(context).firstOrNull()?.parentFile
                }
                else -> listRecordings(context).firstOrNull()?.parentFile
            }
        )
    }

    val transcriptVm: TranscriptViewModel = viewModel()
    val scroll = rememberScrollState()

    Scaffold { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(24.dp)
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TwinMind Demo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(statusText, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            Text(timer, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)

            if (noAudioFlag) {
                Spacer(Modifier.height(8.dp))
                NoAudioWarning(visible = true)
            }

            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                when (status) {
                    RecordingStatus.Idle,
                    is RecordingStatus.Stopped,
                    is RecordingStatus.Error -> {
                        Button(
                            onClick = { recordVm.onRecordPressed(status) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Recording")
                        }
                    }

                    is RecordingStatus.Recording -> {
                        Button(
                            onClick = { recordVm.pause() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Pause") }

                        OutlinedButton(
                            onClick = { recordVm.onStopPressed() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Stop") }
                    }

                    is RecordingStatus.Paused -> {
                        Button(
                            onClick = { recordVm.resume() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Resume") }

                        OutlinedButton(
                            onClick = { recordVm.onStopPressed() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Stop") }
                    }
                }
            }

            val dir = latestSessionDir
            Spacer(Modifier.height(32.dp))
            if (dir != null && dir.exists()) {
                TranscriptPanel(sessionDir = dir, vm = transcriptVm)

                Spacer(Modifier.height(24.dp))
                SummaryPanel(sessionDir = dir)

                Spacer(Modifier.height(24.dp))
                SummaryHistoryList(
                    items = allSummaries,
                    currentSessionId = dir.name,
                    onRegenerate = { sid -> summaryListVm.retry(sid) }
                )
            } else {
                Text("No session yet. Press Record to start.")
            }

        }
    }
}

private fun formatSec(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}

@Composable
fun TranscriptPanel(sessionDir: File, vm: com.example.twinmindproject.transcribe.TranscriptViewModel) {
    val sessionId = sessionDir.name
    LaunchedEffect(sessionId) { vm.setSession(sessionId) }
    val ui by vm.ui.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        Text("Transcript", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (ui.text.isBlank()) {
            Text("Transcribing…")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.retryAll() }) { Text("Retry All") }
        } else {
            Text(ui.text)
            Spacer(Modifier.height(8.dp))
            if (!ui.isComplete) {
                Text("More coming…", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.retryAll() }) { Text("Retry All") }
            }
        }
    }
}


@Composable
fun NoAudioWarning(visible: Boolean) {
    if (!visible) return
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Text(
            "No audio detected – Check microphone",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

