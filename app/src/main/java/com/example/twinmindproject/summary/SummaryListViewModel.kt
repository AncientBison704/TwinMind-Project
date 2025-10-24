package com.example.twinmindproject.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmindproject.data.SummaryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SummaryListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SummaryRepository(app)

    val items: StateFlow<List<SummaryEntity>> =
        repo.all()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retry(sessionId: String) = repo.retry(sessionId)
}

