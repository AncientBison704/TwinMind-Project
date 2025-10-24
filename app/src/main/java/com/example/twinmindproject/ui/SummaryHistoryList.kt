package com.example.twinmindproject.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.twinmindproject.data.SummaryEntity

@Composable
fun SummaryHistoryList(
    items: List<SummaryEntity>,
    currentSessionId: String?,
    onRegenerate: (String) -> Unit
) {
    val others = remember(items, currentSessionId) {
        items.filter { it.sessionId != currentSessionId }
    }
    if (others.isEmpty()) return

    Spacer(Modifier.height(16.dp))
    Text("Previous Summaries", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        others.forEach { ent ->
            SummaryHistoryCard(
                item = ent,
                initiallyExpanded = false,
                onRegenerate = onRegenerate
            )
        }
    }
}
