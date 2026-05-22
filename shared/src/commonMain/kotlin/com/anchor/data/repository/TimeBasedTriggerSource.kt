package com.anchor.data.repository

import com.anchor.domain.model.Sensitivity
import com.anchor.domain.model.SessionOutcome
import com.anchor.domain.model.Trigger
import com.anchor.domain.repository.SessionRepository
import com.anchor.domain.repository.TriggerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.*
import kotlin.random.Random

/**
 * Time-based trigger predictor.
 *
 * Builds an hour-of-day histogram from completed sessions (abandoned sessions are excluded) and
 * emits a [Trigger] whenever the local clock enters an hour with low activity — i.e. a likely
 * free/drift window.
 *
 * Cold-start behaviour: if fewer than 5 sessions exist, falls back to well-known drift windows
 * (mid-morning, post-lunch, evening) so new users still receive useful prompts.
 *
 * Gate: at most one trigger per clock-hour per calendar day to prevent notification spam.
 */
class TimeBasedTriggerSource(
    private val sessions: SessionRepository,
    private val clock: Clock = Clock.System,
    private val tickEvery: Long = 60_000L,
) : TriggerSource {

    override fun observe(sensitivity: Sensitivity): Flow<Trigger> = flow {
        var lastEmittedHour: Int? = null
        var lastEmittedDay: Int? = null

        while (true) {
            val tz = TimeZone.currentSystemDefault()
            val now = clock.now()
            val localNow = now.toLocalDateTime(tz)
            val hour = localNow.hour
            val dayOfYear = localNow.dayOfYear

            // Reset per-hour gate when the calendar day rolls over
            if (dayOfYear != lastEmittedDay) {
                lastEmittedHour = null
            }

            val currentSessions = sessions.sessions.first()
            val completedSessions = currentSessions.filter { it.outcome == SessionOutcome.Completed }
            val totalCompleted = completedSessions.size

            val confidence: Float = if (totalCompleted < 5) {
                // Not enough history — use empirically reliable drift windows.
                // Late night (11pm–6am): very low confidence (user is likely sleeping).
                // Peak drift hours: morning ramp-up (8–10), post-lunch slump (12–14),
                // evening wind-down (18–21).
                when (hour) {
                    in 8..10  -> 0.75f
                    in 12..14 -> 0.65f
                    in 18..21 -> 0.85f
                    in 7..7   -> 0.50f
                    in 15..17 -> 0.55f
                    in 22..23 -> 0.30f
                    in 0..6   -> 0.05f
                    else      -> 0.40f
                }
            } else {
                // Enough history: build hourly histogram from completed sessions only.
                val histogram = IntArray(24)
                completedSessions.forEach { s ->
                    s.completedAt?.toLocalDateTime(tz)?.hour?.let { histogram[it]++ }
                }
                val max = (histogram.maxOrNull() ?: 0).coerceAtLeast(1)
                // High histogram[hour] → user usually works now → low confidence (don't interrupt)
                // Low histogram[hour] → user drifts now         → high confidence (good time to prompt)
                1f - (histogram[hour].toFloat() / max)
            }

            if (confidence >= sensitivity.threshold && hour != lastEmittedHour) {
                emit(
                    Trigger(
                        id = "trg-${now.toEpochMilliseconds()}-${Random.nextInt(0xFFFF)}",
                        predictedAt = now,
                        confidence = confidence,
                        reason = buildReason(hour, totalCompleted, confidence),
                    )
                )
                lastEmittedHour = hour
                lastEmittedDay = dayOfYear
            }

            delay(tickEvery)
        }
    }

    private fun buildReason(hour: Int, totalCompleted: Int, confidence: Float): String {
        val pct = (confidence * 100).toInt()
        return if (totalCompleted < 5) {
            when (hour) {
                in 8..10  -> "Morning window — a high-yield time to start a focused session."
                in 12..14 -> "Post-lunch slump — redirect before momentum is lost."
                in 18..21 -> "Evening drift window — close the day with intention."
                else      -> "Good moment to check in on your focus goals."
            }
        } else {
            "Low historical activity at ${hour.toString().padStart(2, '0')}:00 ($pct% confidence)."
        }
    }
}
