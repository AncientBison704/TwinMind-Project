package com.example.twinmindproject.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.twinmindproject.summary.SummaryViewModel
import java.io.File

@Composable
fun SummaryPanel(sessionDir: File) {
    val sessionId = sessionDir.name
    val vm: SummaryViewModel = viewModel()

    LaunchedEffect(sessionId) { vm.setSession(sessionId) }
    val ui by vm.ui.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        when {
            ui.error != null -> {
                Text("Error: ${ui.error}", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.retry(sessionId) }) { Text("Retry") }
            }
            ui.loading -> {
                Text("Generating summary…")
            }
            else -> {
                Section("Title") { Text(ui.title.ifBlank { "—" }) }
                Section("Summary") { Text(ui.summary.ifBlank { "—" }) }

                Section("Action Items") {
                    if (ui.actionItems.isEmpty()) Text("—")
                    else ui.actionItems.forEach { Text("• $it") }
                }

                Section("Key Points") {
                    if (ui.keyPoints.isEmpty()) Text("—")
                    else ui.keyPoints.forEach { Text("• $it") }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { vm.retry(sessionId) }) { Text("Regenerate") }
            }
        }
    }
}


@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Column(content = content)
}
