package com.anchor.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anchor.domain.repository.HistoryLog
import com.anchor.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val logs: List<HistoryLog> = emptyList()
)

class HistoryViewModel(
    historyRepository: HistoryRepository
) : ViewModel() {
    val state: StateFlow<HistoryUiState> = historyRepository.logs
        .map { HistoryUiState(it.sortedByDescending { log -> log.timestamp }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())
}
