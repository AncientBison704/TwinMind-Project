package com.example.twinmindproject.transcribe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmindproject.data.ChunkStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TranscriptUi(
    val sessionId: String = "",
    val text: String = "",
    val isComplete: Boolean = false
)

class TranscriptViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TranscriptionRepository(app)

    private val _sessionId = MutableStateFlow("")
    val ui: StateFlow<TranscriptUi> =
        _sessionId.filter { it.isNotBlank() }
            .flatMapLatest { sid ->
                repo.transcriptFlow(sid).map { list ->
                    val ordered = list.sortedBy { it.index }
                    val full = buildString {
                        ordered.forEach { e ->
                            if (!e.transcriptText.isNullOrBlank()) {
                                append(e.transcriptText.trim())
                                append('\n')
                            }
                        }
                    }.trim()
                    TranscriptUi(
                        sessionId = sid,
                        text = full,
                        isComplete = ordered.all { it.status == ChunkStatus.DONE }
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TranscriptUi())

    fun setSession(sessionId: String) { _sessionId.value = sessionId }

    fun retryAll() {
        viewModelScope.launch { repo.retryAll() }
    }
}
