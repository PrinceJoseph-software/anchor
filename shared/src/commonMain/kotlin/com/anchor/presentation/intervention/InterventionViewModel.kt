package com.anchor.presentation.intervention

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anchor.domain.model.Action
import com.anchor.domain.model.Sensitivity
import com.anchor.domain.repository.ActionRepository
import com.anchor.domain.repository.SettingsRepository
import com.anchor.domain.usecase.RecordSkip
import com.anchor.domain.usecase.RecordSnooze
import com.anchor.domain.usecase.StartSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InterventionUiState(
    val triggerId: String = "",
    val delaying: Boolean = false,
    val skipping: Boolean = false,
    val skipReason: String = "",
)

/**
 * The Decision Point. Asymmetric friction by design:
 * - Start = single tap.
 * - Delay = hold-to-confirm.
 * - Skip = explicit reason.
 */
class InterventionViewModel(
    private val actionsRepo: ActionRepository,
    private val settings: SettingsRepository,
    private val startSession: StartSession,
    private val recordSnooze: RecordSnooze,
    private val recordSkip: RecordSkip,
) : ViewModel() {

    private val _state = MutableStateFlow(InterventionUiState())
    val state: StateFlow<InterventionUiState> = _state.asStateFlow()

    /** Available focus actions — collected directly in the screen. */
    val actionsFlow: StateFlow<List<Action>> = actionsRepo.actions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val minReasonLength: StateFlow<Int> = settings.sensitivity.map {
        when (it) {
            Sensitivity.High -> 20
            Sensitivity.Medium -> 10
            Sensitivity.Low -> 1
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10)

    fun bind(triggerId: String) { _state.update { it.copy(triggerId = triggerId) } }
    fun openDelay() = _state.update { it.copy(delaying = true, skipping = false) }
    fun openSkip() = _state.update { it.copy(skipping = true, delaying = false) }
    fun setSkipReason(reason: String) = _state.update { it.copy(skipReason = reason) }

    fun start(actionId: String, onStarted: (String) -> Unit) = viewModelScope.launch {
        val session = startSession(actionId, _state.value.triggerId.takeIf { it.isNotEmpty() })
        onStarted(session.id)
    }

    fun confirmDelay(onDone: () -> Unit) = viewModelScope.launch {
        recordSnooze(_state.value.triggerId, "delay")
        onDone()
    }

    fun confirmSkip(onDone: () -> Unit) = viewModelScope.launch {
        recordSkip(_state.value.triggerId, _state.value.skipReason.ifBlank { "unspecified" })
        onDone()
    }
}
