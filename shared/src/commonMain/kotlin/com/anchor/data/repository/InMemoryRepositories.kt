package com.anchor.data.repository

import com.anchor.domain.model.*
import com.anchor.domain.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import com.anchor.domain.repository.LogType
import kotlin.random.Random

private fun id(): String {
    val ts = Clock.System.now().toEpochMilliseconds().toString(36)
    val rand = Random.nextInt(0xFFFF).toString(16).padStart(4, '0')
    return "$ts-$rand"
}

class InMemoryActionRepository : ActionRepository {
    private val state = MutableStateFlow<List<Action>>(emptyList())
    override val actions: StateFlow<List<Action>> = state.asStateFlow()
    override suspend fun add(action: Action) { state.update { it + action } }
    override suspend fun remove(id: String) { state.update { it.filterNot { a -> a.id == id } } }
    override suspend fun replaceAll(actions: List<Action>) { state.value = actions }
}

class InMemorySessionRepository(private val clock: Clock = Clock.System) : SessionRepository {
    private val state = MutableStateFlow<List<Session>>(emptyList())
    override val sessions: StateFlow<List<Session>> = state.asStateFlow()
    override suspend fun start(actionId: String): Session {
        val s = Session(id(), actionId, clock.now())
        state.update { it + s }
        return s
    }
    override suspend fun complete(sessionId: String) = state.update {
        it.map { s -> if (s.id == sessionId) s.copy(completedAt = clock.now(), outcome = SessionOutcome.Completed) else s }
    }
    override suspend fun abandon(sessionId: String) = state.update {
        it.map { s -> if (s.id == sessionId) s.copy(completedAt = clock.now(), outcome = SessionOutcome.Abandoned) else s }
    }
}

class InMemoryDecisionRepository : DecisionRepository {
    private val state = MutableStateFlow<List<Decision>>(emptyList())
    override val decisions: StateFlow<List<Decision>> = state.asStateFlow()
    override suspend fun record(decision: Decision) { state.update { it + decision } }
}

class InMemorySettingsRepository : SettingsRepository {
    private val _sensitivity = MutableStateFlow(Sensitivity.Medium)
    private val _lock = MutableStateFlow(false)
    private val _notif = MutableStateFlow(true)
    private val _target = MutableStateFlow(5)
    private val _minSessionDuration = MutableStateFlow(5)
    private val _interventionLevel = MutableStateFlow(com.anchor.domain.model.InterventionLevel.Medium)
    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    private val _blockAfterMinutes = MutableStateFlow(0)
    private val _reminders = MutableStateFlow<List<kotlinx.datetime.LocalTime>>(emptyList())
    private val _onboarding = MutableStateFlow(false)
    override val sensitivity: StateFlow<Sensitivity> = _sensitivity.asStateFlow()
    override val lockMode: StateFlow<Boolean> = _lock.asStateFlow()
    override val notifications: StateFlow<Boolean> = _notif.asStateFlow()
    override val dailyTarget: StateFlow<Int> = _target.asStateFlow()
    override val minSessionDuration: StateFlow<Int> = _minSessionDuration.asStateFlow()
    override val interventionLevel: StateFlow<com.anchor.domain.model.InterventionLevel> = _interventionLevel.asStateFlow()
    override val blockedApps: StateFlow<Set<String>> = _blocked.asStateFlow()
    override val blockAfterMinutes: StateFlow<Int> = _blockAfterMinutes.asStateFlow()
    override val reminders: StateFlow<List<kotlinx.datetime.LocalTime>> = _reminders.asStateFlow()
    override val onboardingCompleted: StateFlow<Boolean> = _onboarding.asStateFlow()
    override suspend fun setSensitivity(s: Sensitivity) { _sensitivity.value = s }
    override suspend fun setLockMode(enabled: Boolean) { _lock.value = enabled }
    override suspend fun setNotifications(enabled: Boolean) { _notif.value = enabled }
    override suspend fun setDailyTarget(n: Int) { _target.value = n }
    override suspend fun setMinSessionDuration(minutes: Int) { _minSessionDuration.value = minutes }
    override suspend fun setInterventionLevel(level: com.anchor.domain.model.InterventionLevel) { _interventionLevel.value = level }
    override suspend fun setBlockedApps(apps: Set<String>) { _blocked.value = apps }
    override suspend fun setBlockAfterMinutes(minutes: Int) { _blockAfterMinutes.value = minutes }
    override suspend fun addReminder(time: kotlinx.datetime.LocalTime) { _reminders.update { (it + time).distinct().sorted() } }
    override suspend fun removeReminder(time: kotlinx.datetime.LocalTime) { _reminders.update { it - time } }
    override suspend fun setOnboardingCompleted(completed: Boolean) { _onboarding.value = completed }
}

class InMemoryHistoryRepository : HistoryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _logs = MutableStateFlow<List<HistoryLog>>(emptyList())
    override val logs = _logs.asStateFlow()

    override val streak: StateFlow<Int> = _logs.map { computeCurrentStreak(it) }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    override val longestStreak: StateFlow<Int> = _logs.map { computeLongestStreak(it) }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    override suspend fun addLog(log: HistoryLog) {
        _logs.update { it + log }
    }
}

private fun computeCurrentStreak(logs: List<HistoryLog>): Int {
    val tz = TimeZone.currentSystemDefault()
    // Find last STREAK_RESET — only count sessions after it
    val lastReset = logs.filter { it.type == LogType.STREAK_RESET }.maxOfOrNull { it.timestamp }
    val completedDays = logs
        .filter { it.type == LogType.SESSION_COMPLETE && (lastReset == null || it.timestamp > lastReset) }
        .map { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(tz).date }
        .toSortedSet().toList().reversed()
    if (completedDays.isEmpty()) return 0
    var streak = 1
    for (i in 1 until completedDays.size) {
        if (completedDays[i - 1].minus(1, DateTimeUnit.DAY) == completedDays[i]) streak++ else break
    }
    return streak
}

private fun computeLongestStreak(logs: List<HistoryLog>): Int {
    val tz = TimeZone.currentSystemDefault()
    val allDays = logs
        .filter { it.type == LogType.SESSION_COMPLETE }
        .map { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(tz).date }
        .toSortedSet().toList()
    if (allDays.isEmpty()) return 0
    var longest = 1; var current = 1
    for (i in 1 until allDays.size) {
        current = if (allDays[i - 1].plus(1, DateTimeUnit.DAY) == allDays[i]) current + 1 else 1
        if (current > longest) longest = current
    }
    return longest
}

class InMemoryGoalRepository : com.anchor.domain.repository.GoalRepository {
    private val _goals = MutableStateFlow<List<com.anchor.domain.model.Goal>>(emptyList())
    private val _exemptionCards = MutableStateFlow(0)
    override val goals = _goals.asStateFlow()
    override val exemptionCards = _exemptionCards.asStateFlow()
    override suspend fun add(goal: com.anchor.domain.model.Goal) { _goals.update { it + goal } }
    override suspend fun updateProgress(goalId: String, sessionsCompleted: Int) {
        _goals.update { list -> list.map { if (it.id == goalId) it.copy(sessionsCompleted = sessionsCompleted) else it } }
    }
    override suspend fun markCompleted(goalId: String) {
        _goals.update { list ->
            list.map {
                if (it.id == goalId && it.status == com.anchor.domain.model.GoalStatus.Active)
                    it.copy(status = com.anchor.domain.model.GoalStatus.Completed, completedAt = Clock.System.now(), exemptionCardsAwarded = 1)
                else it
            }
        }
    }
    override suspend fun markFailed(goalId: String) {
        _goals.update { list ->
            list.map {
                if (it.id == goalId && it.status == com.anchor.domain.model.GoalStatus.Active)
                    it.copy(status = com.anchor.domain.model.GoalStatus.Failed)
                else it
            }
        }
    }
    override suspend fun awardExemptionCards(count: Int) { _exemptionCards.update { it + count } }
    override suspend fun useExemptionCard(): Boolean {
        var wasUsed = false
        _exemptionCards.update { current ->
            if (current > 0) { wasUsed = true; current - 1 } else current
        }
        return wasUsed
    }
    override suspend fun replaceAll(goals: List<com.anchor.domain.model.Goal>) { _goals.value = goals }
}
