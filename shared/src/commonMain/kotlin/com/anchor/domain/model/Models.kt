package com.anchor.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Action(
    val id: String,
    val name: String,
    val icon: ActionIcon = ActionIcon.Generic,
    val isCustom: Boolean = false,
)

@Serializable
enum class ActionIcon { Study, Workout, Reading, Generic }

@Serializable
data class Session(
    val id: String,
    val actionId: String,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val outcome: SessionOutcome = SessionOutcome.InProgress,
)

@Serializable
enum class SessionOutcome { InProgress, Completed, Abandoned }

@Serializable
data class Trigger(
    val id: String,
    val predictedAt: Instant,
    val confidence: Float, // 0f..1f
    val reason: String,
)

@Serializable
enum class Sensitivity(val threshold: Float) {
    Low(0.8f), Medium(0.6f), High(0.4f);
}

@Serializable
enum class InterventionLevel {
    Low,      // Notification only - user can continue or decide to start
    Medium,   // Decision popup appears, might shift to app
    High;     // Fully locked out until action is taken or minimum time is met
}

@Serializable
data class Decision(
    val triggerId: String,
    val at: Instant,
    val kind: Kind,
    val snoozeReason: String? = null,
) {
    @Serializable
    enum class Kind { Started, Snoozed, Missed }
}

data class ReliabilityScore(val followThroughRate: Float, val sampleSize: Int) {
    val percent: Int get() = (followThroughRate * 100).toInt()
}

@Serializable
data class Goal(
    val id: String,
    val title: String,
    val targetSessions: Int,
    val durationDays: Int,
    val startedAt: Instant,
    val deadlineAt: Instant,
    val completedAt: Instant? = null,
    val status: GoalStatus = GoalStatus.Active,
    val sessionsCompleted: Int = 0,
    val exemptionCardsAwarded: Int = 0, // tracks award so it can't be awarded twice
)

@Serializable
enum class GoalStatus { Active, Completed, Failed }
