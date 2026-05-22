package com.anchor.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anchor.domain.model.Session
import com.anchor.domain.model.SessionOutcome
import com.anchor.domain.repository.ActionRepository
import com.anchor.domain.repository.SessionRepository
import com.anchor.domain.repository.SettingsRepository
import com.anchor.domain.usecase.AbandonSession
import com.anchor.domain.usecase.CompleteSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class SessionUiState(
    val session: Session? = null,
    val actionName: String = "Focus",
    val minDurationMinutes: Int = 5,
    val now: Instant = Clock.System.now(),
    val showAbandonConfirm: Boolean = false,
) {
    val elapsedSeconds: Long
        get() = session?.let { (now - it.startedAt).inWholeSeconds.coerceAtLeast(0) } ?: 0
    val displayMinutes: Long get() = elapsedSeconds / 60
    val displaySeconds: Long get() = elapsedSeconds % 60
    val canComplete: Boolean get() = elapsedSeconds >= minDurationMinutes.toLong() * 60
    val remainingMinutes: Long get() = (minDurationMinutes.toLong() - displayMinutes).coerceAtLeast(0)
    val isCompleted: Boolean get() = session?.outcome == SessionOutcome.Completed
    val isAbandoned: Boolean get() = session?.outcome == SessionOutcome.Abandoned
    /** Seconds the user actually spent before abandoning. */
    val abandonedDurationSeconds: Long get() {
        val s = session ?: return 0
        val ended = s.completedAt ?: return 0
        return (ended - s.startedAt).inWholeSeconds.coerceAtLeast(0)
    }
    /** Seconds spent in a successfully completed session. */
    val completedDurationSeconds: Long get() {
        val s = session ?: return 0
        if (s.outcome != SessionOutcome.Completed) return 0
        val ended = s.completedAt ?: return 0
        return (ended - s.startedAt).inWholeSeconds.coerceAtLeast(0)
    }
}

class SessionViewModel(
    private val sessionId: String,
    sessionsRepo: SessionRepository,
    actionsRepo: ActionRepository,
    settingsRepo: SettingsRepository,
    private val completeSession: CompleteSession,
    private val abandonSession: AbandonSession,
) : ViewModel() {

    private val _now = MutableStateFlow(Clock.System.now())
    private val _showAbandonConfirm = MutableStateFlow(false)

    val state: StateFlow<SessionUiState> =
        combine(
            sessionsRepo.sessions,
            actionsRepo.actions,
            settingsRepo.minSessionDuration,
            _now,
            _showAbandonConfirm,
        ) { sessions, actions, minDuration, now, showAbandon ->
            val session = sessions.firstOrNull { it.id == sessionId }
            val actionName = actions.firstOrNull { it.id == session?.actionId }?.name ?: "Focus"
            SessionUiState(
                session = session,
                actionName = actionName,
                minDurationMinutes = minDuration,
                now = now,
                showAbandonConfirm = showAbandon,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                _now.value = Clock.System.now()
            }
        }
    }

    fun complete() = viewModelScope.launch {
        completeSession(sessionId)
    }

    fun requestAbandon() {
        _showAbandonConfirm.update { true }
    }

    fun dismissAbandon() {
        _showAbandonConfirm.update { false }
    }

    fun confirmAbandon() = viewModelScope.launch {
        abandonSession(sessionId)
        _showAbandonConfirm.update { false }
    }
}
