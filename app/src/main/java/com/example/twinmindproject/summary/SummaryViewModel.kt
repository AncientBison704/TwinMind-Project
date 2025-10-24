package com.example.twinmindproject.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmindproject.data.SummaryEntity
import com.example.twinmindproject.data.SummaryStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SummaryUi(
    val loading: Boolean = false,
    val error: String? = null,
    val title: String = "",
    val summary: String = "",
    val actionItems: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val draft: String = ""
)

class SummaryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SummaryRepository(app)

    private val sessionIdFlow = MutableStateFlow<String?>(null)

    private var enqueueJob: Job? = null
    private var hasEnqueuedForSession: String? = null

    val ui: StateFlow<SummaryUi> =
        sessionIdFlow
            .filterNotNull()
            .flatMapLatest { sid ->
                val summaryRowFlow: Flow<SummaryEntity?> = repo.flow(sid)

                val allDoneFlow: Flow<Boolean> = repo.observeAllTranscribed(sid)

                allDoneFlow
                    .onEach { allDone ->
                        if (allDone && hasEnqueuedForSession != sid) {
                            enqueueJob?.cancel()
                            enqueueJob = viewModelScope.launch {
                                repo.ensureRow(sid)
                                repo.enqueue(sid)
                                hasEnqueuedForSession = sid
                            }
                        }
                    }
                    .launchIn(viewModelScope)

                combine(summaryRowFlow, allDoneFlow) { ent, allDone ->
                    val base = toUi(ent)
                    val effectiveLoading = !allDone || base.loading
                    val effectiveError = if (!allDone) null else base.error
                    base.copy(
                        loading = effectiveLoading,
                        error = effectiveError,
                        draft = base.draft
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SummaryUi())

    private fun toUi(ent: SummaryEntity?): SummaryUi {
        if (ent == null) return SummaryUi(loading = true)
        val loading = ent.status == SummaryStatus.RUNNING ||
                (ent.status == SummaryStatus.PENDING && ent.draft.isNotBlank())
        val err = if (ent.status == SummaryStatus.ERROR) ent.error else null
        val actions = ent.actionItems?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        val keys = ent.keyPoints?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        return SummaryUi(
            loading = loading,
            error = err,
            title = ent.title.orEmpty(),
            summary = ent.summary.orEmpty(),
            actionItems = actions,
            keyPoints = keys,
            draft = ent.draft
        )
    }

    fun setSession(sessionId: String) {
        sessionIdFlow.value = sessionId
        hasEnqueuedForSession = null
        viewModelScope.launch { repo.ensureRow(sessionId) }
    }

    fun generate(sessionId: String) {
        viewModelScope.launch {
            repo.ensureRow(sessionId)
            repo.enqueue(sessionId)
            hasEnqueuedForSession = sessionId
        }
    }
    fun retry(sessionId: String) = generate(sessionId)
}

