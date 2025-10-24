package com.example.twinmindproject.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.twinmindproject.data.SummaryEntity
import com.example.twinmindproject.data.SummaryStatus

@Composable
fun SummaryHistoryCard(
    item: SummaryEntity,
    initiallyExpanded: Boolean,
    onRegenerate: ((String) -> Unit)? = null
) {
    var expanded by remember(item.sessionId) { mutableStateOf(initiallyExpanded) }

    val titleText = item.title?.ifBlank { "Untitled" } ?: "Untitled"
    val subtitle = when (item.status) {
        SummaryStatus.DONE    -> "Completed"
        SummaryStatus.RUNNING -> "Generating…"
        SummaryStatus.PENDING -> "Pending"
        SummaryStatus.ERROR   -> item.error ?: "Error"
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(titleText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))

                    SectionHeader("Title")
                    Text(item.title?.ifBlank { "—" } ?: "—")

                    Spacer(Modifier.height(12.dp))
                    SectionHeader("Summary")
                    val summaryText = item.summary?.ifBlank { item.draft } ?: item.draft
                    Text(summaryText.ifBlank { "—" })

                    Spacer(Modifier.height(12.dp))
                    SectionHeader("Action Items")
                    val actions = item.actionItems
                        ?.lines()
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    if (actions.isEmpty()) {
                        Text("—")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            actions.forEach { Text("• $it") }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    SectionHeader("Key Points")
                    val keys = item.keyPoints
                        ?.lines()
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    if (keys.isEmpty()) {
                        Text("—")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            keys.forEach { Text("• $it") }
                        }
                    }

                    if (onRegenerate != null) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { onRegenerate(item.sessionId) }) {
                            Text("Regenerate")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}
