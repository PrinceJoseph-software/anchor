package com.anchor.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anchor.domain.model.SessionOutcome
import com.anchor.domain.repository.SessionRepository
import com.anchor.domain.usecase.ComputeReliability
import com.anchor.domain.usecase.ComputeStreak
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*

data class StatsState(
    val followThroughPct: Int = 0,
    val sample: Int = 0,
    val weekly: List<Int> = List(7) { 0 }, // Mon..Sun
    val totalSessions: Int = 0,
    val thisWeek: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val avgSessionMinutes: Int = 0,
)

class StatsViewModel(
    sessions: SessionRepository,
    reliability: ComputeReliability,
    streak: ComputeStreak,
) : ViewModel() {

    val state: StateFlow<StatsState> =
        combine(sessions.sessions, reliability(), streak()) { all, rel, st ->
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(tz).date
            val mondayOffset = (today.dayOfWeek.isoDayNumber - 1)
            val monday = today.minus(mondayOffset, DateTimeUnit.DAY)
            val weekly = IntArray(7)
            var thisWeek = 0
            val completed = all.filter { it.outcome == SessionOutcome.Completed }
            completed.forEach { s ->
                val d = s.completedAt?.toLocalDateTime(tz)?.date ?: return@forEach
                if (d in monday..today) {
                    val idx = d.dayOfWeek.isoDayNumber - 1
                    weekly[idx]++; thisWeek++
                }
            }
            val avgMinutes = if (completed.isEmpty()) 0 else {
                val totalSec = completed.sumOf { s ->
                    val end = s.completedAt ?: return@sumOf 0L
                    (end - s.startedAt).inWholeSeconds.coerceAtLeast(0L)
                }
                (totalSec / completed.size / 60L).toInt()
            }
            StatsState(
                followThroughPct = rel.percent,
                sample = rel.sampleSize,
                weekly = weekly.toList(),
                totalSessions = completed.size,
                thisWeek = thisWeek,
                currentStreak = st.current,
                longestStreak = st.longest,
                avgSessionMinutes = avgMinutes,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())
}
