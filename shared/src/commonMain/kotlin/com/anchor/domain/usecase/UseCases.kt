package com.anchor.domain.usecase

import com.anchor.domain.model.*
import com.anchor.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.*

class StartSession(
    private val sessions: SessionRepository,
    private val decisions: DecisionRepository,
    private val history: HistoryRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(actionId: String, triggerId: String? = null): Session {
        triggerId?.let {
            decisions.record(Decision(it, clock.now(), Decision.Kind.Started))
        }
        return sessions.start(actionId)
    }
}

class CompleteSession(
    private val sessions: SessionRepository,
    private val actions: ActionRepository,
    private val history: HistoryRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(sessionId: String) {
        val session = sessions.sessions.first().firstOrNull { it.id == sessionId } ?: return
        sessions.complete(sessionId)
        val actionName = actions.actions.first().firstOrNull { it.id == session.actionId }?.name
        history.addLog(
            HistoryLog(
                timestamp = clock.now().toEpochMilliseconds(),
                type = LogType.SESSION_COMPLETE,
                actionName = actionName,
            )
        )
    }
}

class AbandonSession(
    private val sessions: SessionRepository,
    private val actions: ActionRepository,
    private val history: HistoryRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(sessionId: String) {
        val session = sessions.sessions.first().firstOrNull { it.id == sessionId } ?: return
        sessions.abandon(sessionId)
        val actionName = actions.actions.first().firstOrNull { it.id == session.actionId }?.name
        history.addLog(
            HistoryLog(
                timestamp = clock.now().toEpochMilliseconds(),
                type = LogType.SESSION_ABANDONED,
                actionName = actionName,
            )
        )
    }
}

class RecordSnooze(
    private val decisions: DecisionRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(triggerId: String, reason: String) {
        decisions.record(Decision(triggerId, clock.now(), Decision.Kind.Snoozed, reason))
    }
}

class RecordSkip(
    private val decisions: DecisionRepository,
    private val history: HistoryRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(triggerId: String, reason: String) {
        decisions.record(Decision(triggerId, clock.now(), Decision.Kind.Missed, reason))
        history.addLog(HistoryLog(
            timestamp = clock.now().toEpochMilliseconds(),
            type = LogType.SESSION_SKIPPED,
            reason = reason
        ))
    }
}

class ComputeReliability(
    private val decisions: DecisionRepository,
    private val clock: Clock = Clock.System,
) {
    operator fun invoke(windowDays: Int = 7): Flow<ReliabilityScore> =
        decisions.decisions.map { all ->
            val cutoff = clock.now().minus(windowDays, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
            val recent = all.filter { it.at >= cutoff }
            if (recent.isEmpty()) ReliabilityScore(0f, 0)
            else {
                val started = recent.count { it.kind == Decision.Kind.Started }
                ReliabilityScore(started.toFloat() / recent.size, recent.size)
            }
        }
}

class TodaysProgress(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.System,
) {
    data class Progress(val completed: Int, val target: Int)
    operator fun invoke(): Flow<Progress> =
        combine(sessions.sessions, settings.dailyTarget) { all, target ->
            val tz = TimeZone.currentSystemDefault()
            val today = clock.now().toLocalDateTime(tz).date
            val done = all.count {
                it.outcome == SessionOutcome.Completed &&
                    it.completedAt?.toLocalDateTime(tz)?.date == today
            }
            Progress(done, target)
        }
}

class ComputeStreak(
    private val sessions: SessionRepository,
    private val clock: Clock = Clock.System,
) {
    data class Streak(val current: Int, val longest: Int)
    operator fun invoke(): Flow<Streak> = sessions.sessions.map { all ->
        val tz = TimeZone.currentSystemDefault()
        val days = all.filter { it.outcome == SessionOutcome.Completed }
            .mapNotNull { it.completedAt?.toLocalDateTime(tz)?.date }
            .toSortedSet()
        if (days.isEmpty()) return@map Streak(0, 0)
        var longest = 1; var run = 1
        val list = days.toList()
        for (i in 1 until list.size) {
            run = if (list[i - 1].plus(1, DateTimeUnit.DAY) == list[i]) run + 1 else 1
            if (run > longest) longest = run
        }
        val today = clock.now().toLocalDateTime(tz).date
        var current = 0; var cursor = today
        while (days.contains(cursor)) { current++; cursor = cursor.minus(1, DateTimeUnit.DAY) }
        Streak(current, longest)
    }
}
