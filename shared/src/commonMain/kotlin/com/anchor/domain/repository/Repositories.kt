package com.anchor.domain.repository

import com.anchor.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface ActionRepository {
    val actions: Flow<List<Action>>
    suspend fun add(action: Action)
    suspend fun remove(id: String)
    suspend fun replaceAll(actions: List<Action>)
}

interface SessionRepository {
    val sessions: Flow<List<Session>>
    suspend fun start(actionId: String): Session
    suspend fun complete(sessionId: String)
    suspend fun abandon(sessionId: String)
}

interface DecisionRepository {
    val decisions: Flow<List<Decision>>
    suspend fun record(decision: Decision)
}

interface SettingsRepository {
    val sensitivity: Flow<Sensitivity>
    val lockMode: Flow<Boolean>
    val notifications: Flow<Boolean>
    val dailyTarget: Flow<Int>
    val minSessionDuration: Flow<Int>
    val interventionLevel: Flow<com.anchor.domain.model.InterventionLevel>
    val blockedApps: Flow<Set<String>>
    val blockAfterMinutes: Flow<Int>
    val reminders: Flow<List<kotlinx.datetime.LocalTime>>
    val onboardingCompleted: Flow<Boolean>
    suspend fun setSensitivity(s: Sensitivity)
    suspend fun setLockMode(enabled: Boolean)
    suspend fun setNotifications(enabled: Boolean)
    suspend fun setDailyTarget(n: Int)
    suspend fun setMinSessionDuration(minutes: Int)
    suspend fun setInterventionLevel(level: com.anchor.domain.model.InterventionLevel)
    suspend fun setBlockedApps(apps: Set<String>)
    suspend fun setBlockAfterMinutes(minutes: Int)
    suspend fun addReminder(time: kotlinx.datetime.LocalTime)
    suspend fun removeReminder(time: kotlinx.datetime.LocalTime)
    suspend fun setOnboardingCompleted(completed: Boolean)
}

@Serializable
data class HistoryLog(
    val timestamp: Long,
    val type: LogType,
    val reason: String? = null,
    val actionName: String? = null,
)

@Serializable
enum class LogType {
    SESSION_COMPLETE,
    SESSION_SKIPPED,
    SESSION_ABANDONED,
    STREAK_RESET,   // emitted when a goal deadline is missed — streak calculation resets from this point
}

interface HistoryRepository {
    val logs: Flow<List<HistoryLog>>
    suspend fun addLog(log: HistoryLog)
    val streak: Flow<Int>
    val longestStreak: Flow<Int>
}

interface GoalRepository {
    val goals: Flow<List<Goal>>
    val exemptionCards: Flow<Int>
    suspend fun add(goal: Goal)
    suspend fun updateProgress(goalId: String, sessionsCompleted: Int)
    suspend fun markCompleted(goalId: String)
    suspend fun markFailed(goalId: String)
    suspend fun awardExemptionCards(count: Int)
    suspend fun useExemptionCard(): Boolean  // returns true if a card was available
    suspend fun replaceAll(goals: List<Goal>)
}

interface TriggerSource {
    /** Cold flow of predicted intervention moments. */
    fun observe(sensitivity: Sensitivity): Flow<Trigger>
}
