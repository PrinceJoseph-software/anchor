package com.anchor.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anchor.domain.model.Goal
import com.anchor.domain.model.GoalStatus
import com.anchor.domain.model.SessionOutcome
import com.anchor.domain.repository.GoalRepository
import com.anchor.domain.repository.HistoryLog
import com.anchor.domain.repository.HistoryRepository
import com.anchor.domain.repository.LogType
import com.anchor.domain.repository.SessionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

data class GoalUiState(
    val activeGoal: Goal? = null,
    val completedGoals: List<Goal> = emptyList(),
    val failedGoals: List<Goal> = emptyList(),
    val exemptionCards: Int = 0,
    val currentSessions: Int = 0,   // sessions completed since goal start
    val daysRemaining: Int = 0,
)

class GoalViewModel(
    private val goalRepo: GoalRepository,
    private val sessions: SessionRepository,
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    val state: StateFlow<GoalUiState> = combine(
        goalRepo.goals,
        goalRepo.exemptionCards,
        sessions.sessions,
    ) { goals, cards, allSessions ->
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val activeGoal = goals.firstOrNull { it.status == GoalStatus.Active }
        val currentCount = if (activeGoal != null) {
            allSessions.count { s ->
                s.outcome == SessionOutcome.Completed &&
                        s.completedAt != null &&
                        s.completedAt!! >= activeGoal.startedAt
            }
        } else 0
        val daysRemaining = if (activeGoal != null) {
            val todayDate = now.toLocalDateTime(tz).date
            val deadlineDate = activeGoal.deadlineAt.toLocalDateTime(tz).date
            todayDate.daysUntil(deadlineDate).coerceAtLeast(0)
        } else 0
        GoalUiState(
            activeGoal = activeGoal,
            completedGoals = goals.filter { it.status == GoalStatus.Completed },
            failedGoals = goals.filter { it.status == GoalStatus.Failed },
            exemptionCards = cards,
            currentSessions = currentCount,
            daysRemaining = daysRemaining,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalUiState())

    init {
        // Side-effect collector: handles goal state transitions (complete / fail)
        viewModelScope.launch {
            combine(goalRepo.goals, sessions.sessions) { g, s -> Pair(g, s) }.collect { (goals, allSessions) ->
                val now = Clock.System.now()
                goals.filter { it.status == GoalStatus.Active }.forEach { goal ->
                    val sessionsForGoal = allSessions.count { s ->
                        s.outcome == SessionOutcome.Completed &&
                                s.completedAt != null &&
                                s.completedAt!! >= goal.startedAt
                    }
                    // Sync progress if changed
                    if (sessionsForGoal != goal.sessionsCompleted) {
                        goalRepo.updateProgress(goal.id, sessionsForGoal)
                    }
                    when {
                        // Goal achieved — award exemption card only once per goal
                        sessionsForGoal >= goal.targetSessions && goal.exemptionCardsAwarded == 0 -> {
                            goalRepo.markCompleted(goal.id)
                            goalRepo.awardExemptionCards(1)
                        }
                        // Deadline passed without completion — reset streak as penalty
                        now > goal.deadlineAt -> {
                            goalRepo.markFailed(goal.id)
                            historyRepo.addLog(
                                HistoryLog(
                                    timestamp = now.toEpochMilliseconds(),
                                    type = LogType.STREAK_RESET,
                                    reason = "Goal '${goal.title}' failed — streak reset as penalty",
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun createGoal(title: String, targetSessions: Int, durationDays: Int) = viewModelScope.launch {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        // Deadline = end of the day that is durationDays from now
        val deadlineDate = now.toLocalDateTime(tz).date.plus(durationDays, DateTimeUnit.DAY)
        val deadline = LocalDateTime(deadlineDate, LocalTime(23, 59, 59)).toInstant(tz)
        goalRepo.add(
            Goal(
                id = "goal-${now.toEpochMilliseconds()}",
                title = title.trim(),
                targetSessions = targetSessions,
                durationDays = durationDays,
                startedAt = now,
                deadlineAt = deadline,
            )
        )
    }
}
