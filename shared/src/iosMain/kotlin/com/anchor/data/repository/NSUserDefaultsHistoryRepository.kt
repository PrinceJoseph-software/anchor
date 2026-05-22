package com.anchor.data.repository

import com.anchor.domain.repository.HistoryLog
import com.anchor.domain.repository.HistoryRepository
import com.anchor.domain.repository.LogType
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import platform.Foundation.NSUserDefaults

class NSUserDefaultsHistoryRepository : HistoryRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val defaults = NSUserDefaults.standardUserDefaults
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
        val json = defaults.stringForKey("logs_list_v2") ?: return emptyList()
        AnchorJson.decodeFromString(ListSerializer(HistoryLog.serializer()), json)
    }.getOrDefault(emptyList())

    private fun saveLogs(logs: List<HistoryLog>) {
        val json = AnchorJson.encodeToString(ListSerializer(HistoryLog.serializer()), logs)
        defaults.setObject(json, "logs_list_v2")
        defaults.synchronize()
    }
}
