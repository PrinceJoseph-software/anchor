package com.anchor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.anchor.domain.model.Session
import com.anchor.domain.model.SessionOutcome
import com.anchor.domain.repository.HistoryLog
import com.anchor.domain.repository.HistoryRepository
import com.anchor.domain.repository.LogType
import com.anchor.domain.repository.SessionRepository
import com.anchor.platform.AndroidContextHolder
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
import kotlinx.serialization.builtins.ListSerializer
import kotlin.random.Random

class AndroidSessionRepository(
    context: Context = AndroidContextHolder.application,
    private val clock: Clock = Clock.System,
) : SessionRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anchor_sessions", Context.MODE_PRIVATE)
    private val state = MutableStateFlow<List<Session>>(loadSessions())
    override val sessions: StateFlow<List<Session>> = state.asStateFlow()

    override suspend fun start(actionId: String): Session {
        val session = Session(Random.nextLong().toString(16), actionId, clock.now())
        state.update { sessions ->
            val updated = sessions + session
            if (updated.size > 500) updated.takeLast(500) else updated
        }
        saveSessions(state.value)
        return session
    }

    override suspend fun complete(sessionId: String) {
        state.update { sessions ->
            sessions.map { s ->
                if (s.id == sessionId) s.copy(completedAt = clock.now(), outcome = SessionOutcome.Completed) else s
            }
        }
        saveSessions(state.value)
    }

    override suspend fun abandon(sessionId: String) {
        state.update { sessions ->
            sessions.map { s ->
                if (s.id == sessionId) s.copy(completedAt = clock.now(), outcome = SessionOutcome.Abandoned) else s
            }
        }
        saveSessions(state.value)
    }

    private fun loadSessions(): List<Session> = runCatching {
        val json = prefs.getString("sessions_list_v2", null)
            ?: return migrateLegacy()
        val sessions = AnchorJson.decodeFromString(ListSerializer(Session.serializer()), json)
        // Any session that is still InProgress when the app starts was interrupted (crash / kill).
        // Mark it Abandoned so it doesn't appear as active or pollute the today's list.
        val cleaned = sessions.map { s ->
            if (s.outcome == SessionOutcome.InProgress)
                s.copy(completedAt = clock.now(), outcome = SessionOutcome.Abandoned)
            else s
        }
        if (cleaned != sessions) saveSessions(cleaned)
        cleaned
    }.getOrDefault(emptyList())

    private fun saveSessions(sessions: List<Session>) {
        val json = AnchorJson.encodeToString(ListSerializer(Session.serializer()), sessions)
        prefs.edit().putString("sessions_list_v2", json).apply()
    }

    /** One-time migration from the old pipe-delimited format. */
    private fun migrateLegacy(): List<Session> {
        val raw = prefs.getString("sessions_list", null) ?: return emptyList()
        if (raw.isBlank() || raw == "[]") return emptyList()
        return raw.split("|").mapNotNull { part ->
            val cols = part.split("::")
            if (cols.size >= 5) runCatching {
                Session(
                    id = cols[0],
                    actionId = cols[1],
                    startedAt = kotlinx.datetime.Instant.parse(cols[2]),
                    completedAt = if (cols[3] == "null") null else kotlinx.datetime.Instant.parse(cols[3]),
                    outcome = SessionOutcome.valueOf(cols[4]),
                )
            }.getOrNull() else null
        }.also { migrated ->
            // Persist migrated data in new format and remove old key
            saveSessions(migrated)
            prefs.edit().remove("sessions_list").apply()
        }
    }
}

class AndroidHistoryRepository(
    context: Context = AndroidContextHolder.application,
    private val clock: Clock = Clock.System,
) : HistoryRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("anchor_history", Context.MODE_PRIVATE)
    private val _logs = MutableStateFlow<List<HistoryLog>>(loadLogs())
    override val logs: StateFlow<List<HistoryLog>> = _logs.asStateFlow()

    override val streak: StateFlow<Int> = _logs.map { logs ->
        val tz = TimeZone.currentSystemDefault()
        val lastReset = logs.filter { it.type == LogType.STREAK_RESET }.maxOfOrNull { it.timestamp }
        val completedDays = logs
            .filter { it.type == LogType.SESSION_COMPLETE && (lastReset == null || it.timestamp > lastReset) }
            .map { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(tz).date }
            .toSortedSet().toList().reversed()
        if (completedDays.isEmpty()) return@map 0
        var streak = 1
        for (i in 1 until completedDays.size) {
            if (completedDays[i - 1].minus(1, DateTimeUnit.DAY) == completedDays[i]) streak++ else break
        }
        streak
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    override val longestStreak: StateFlow<Int> = _logs.map { logs ->
        val tz = TimeZone.currentSystemDefault()
        val allDays = logs
            .filter { it.type == LogType.SESSION_COMPLETE }
            .map { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(tz).date }
            .toSortedSet().toList()
        if (allDays.isEmpty()) return@map 0
        var longest = 1; var current = 1
        for (i in 1 until allDays.size) {
            current = if (allDays[i - 1].plus(1, DateTimeUnit.DAY) == allDays[i]) current + 1 else 1
            if (current > longest) longest = current
        }
        longest
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    override suspend fun addLog(log: HistoryLog) {
        _logs.update { it + log }
        saveLogs(_logs.value)
    }

    private fun loadLogs(): List<HistoryLog> = runCatching {
        val json = prefs.getString("logs_list_v2", null)
            ?: return migrateLegacyLogs()
        AnchorJson.decodeFromString(ListSerializer(HistoryLog.serializer()), json)
    }.getOrDefault(emptyList())

    private fun saveLogs(logs: List<HistoryLog>) {
        val json = AnchorJson.encodeToString(ListSerializer(HistoryLog.serializer()), logs)
        prefs.edit().putString("logs_list_v2", json).apply()
    }

    private fun migrateLegacyLogs(): List<HistoryLog> {
        val raw = prefs.getString("logs_list", null) ?: return emptyList()
        if (raw.isBlank() || raw == "[]") return emptyList()
        return raw.split("|").mapNotNull { part ->
            val cols = part.split("::")
            if (cols.size >= 4) runCatching {
                HistoryLog(
                    timestamp = cols[0].toLong(),
                    type = LogType.valueOf(cols[1]),
                    reason = cols[2].ifEmpty { null },
                    actionName = cols[3].ifEmpty { null },
                )
            }.getOrNull() else null
        }.also { migrated ->
            saveLogs(migrated)
            prefs.edit().remove("logs_list").apply()
        }
    }
}
