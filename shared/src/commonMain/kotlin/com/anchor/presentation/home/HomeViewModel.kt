package com.anchor.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anchor.domain.model.*
import com.anchor.domain.repository.*
import com.anchor.domain.usecase.CompleteSession
import com.anchor.domain.usecase.StartSession
import com.anchor.domain.usecase.TodaysProgress
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

data class HomeState(
    val today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val progress: TodaysProgress.Progress = TodaysProgress.Progress(0, 5),
    val nextTrigger: Trigger? = null,
    val todaysSessions: List<Session> = emptyList(),
    val actions: List<Action> = emptyList(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val actionsRepo: ActionRepository,
    private val sessionsRepo: SessionRepository,
    private val triggers: TriggerSource,
    private val settings: SettingsRepository,
    private val startSession: StartSession,
    private val completeSession: CompleteSession,
) : ViewModel() {

    val state: StateFlow<HomeState> =
        combine(
            actionsRepo.actions,
            sessionsRepo.sessions,
            settings.sensitivity.flatMapLatest { triggers.observe(it) }.onStart<Trigger?> { emit(null) },
            settings.dailyTarget,
        ) { actions, sessions, trigger, target ->
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val today = now.toLocalDateTime(tz).date
            val currentHour = now.toLocalDateTime(tz).hour
            val done = sessions.count {
                it.outcome == SessionOutcome.Completed &&
                    it.completedAt?.toLocalDateTime(tz)?.date == today
            }
            val todaysSessions = sessions.filter { s ->
                s.startedAt.toLocalDateTime(tz).date == today
            }
            // Only show a trigger if it was predicted in the current hour — stale triggers
            // (from a previous hour) no longer reflect "right now" so we hide them.
            val freshTrigger = trigger?.takeIf {
                it.predictedAt.toLocalDateTime(tz).hour == currentHour
            }
            HomeState(
                today = today,
                progress = TodaysProgress.Progress(done, target),
                nextTrigger = freshTrigger,
                todaysSessions = todaysSessions,
                actions = actions,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun startNow(actionId: String, onStarted: (String) -> Unit) = viewModelScope.launch {
        val session = startSession(actionId, state.value.nextTrigger?.id)
        onStarted(session.id)
    }

    fun complete(sessionId: String) = viewModelScope.launch {
        completeSession(sessionId)
    }
}
