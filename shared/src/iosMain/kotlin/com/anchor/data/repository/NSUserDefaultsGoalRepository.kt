package com.anchor.data.repository

import com.anchor.domain.model.Goal
import com.anchor.domain.model.GoalStatus
import com.anchor.domain.repository.GoalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import platform.Foundation.NSUserDefaults

class NSUserDefaultsGoalRepository : GoalRepository {

    private val defaults = NSUserDefaults.standardUserDefaults
    private val _goals = MutableStateFlow<List<Goal>>(loadGoals())
    private val _exemptionCards = MutableStateFlow(
        (defaults.objectForKey("exemption_cards")?.let { defaults.integerForKey("exemption_cards").toInt() }) ?: 0
    )

    override val goals: StateFlow<List<Goal>> = _goals.asStateFlow()
    override val exemptionCards: StateFlow<Int> = _exemptionCards.asStateFlow()

    override suspend fun add(goal: Goal) {
        _goals.update { it + goal }
        persist()
    }

    override suspend fun updateProgress(goalId: String, sessionsCompleted: Int) {
        _goals.update { list ->
            list.map { if (it.id == goalId) it.copy(sessionsCompleted = sessionsCompleted) else it }
        }
        persist()
    }

    override suspend fun markCompleted(goalId: String) {
        _goals.update { list ->
            list.map {
                if (it.id == goalId && it.status == GoalStatus.Active)
                    it.copy(
                        status = GoalStatus.Completed,
                        completedAt = Clock.System.now(),
                        exemptionCardsAwarded = 1,
                    )
                else it
            }
        }
        persist()
    }

    override suspend fun markFailed(goalId: String) {
        _goals.update { list ->
            list.map {
                if (it.id == goalId && it.status == GoalStatus.Active)
                    it.copy(status = GoalStatus.Failed)
                else it
            }
        }
        persist()
    }

    override suspend fun awardExemptionCards(count: Int) {
        _exemptionCards.update { it + count }
        defaults.setInteger(_exemptionCards.value.toLong(), "exemption_cards")
        defaults.synchronize()
    }

    override suspend fun useExemptionCard(): Boolean {
        val cur = _exemptionCards.value
        return if (cur > 0) {
            _exemptionCards.value = cur - 1
            defaults.setInteger(_exemptionCards.value.toLong(), "exemption_cards")
            defaults.synchronize()
            true
        } else false
    }

    override suspend fun replaceAll(goals: List<Goal>) {
        _goals.value = goals
        persist()
    }

    private fun loadGoals(): List<Goal> = runCatching {
        val json = defaults.stringForKey("goals_v1") ?: return emptyList()
        AnchorJson.decodeFromString(ListSerializer(Goal.serializer()), json)
    }.getOrDefault(emptyList())

    private fun persist() {
        val json = AnchorJson.encodeToString(ListSerializer(Goal.serializer()), _goals.value)
        defaults.setObject(json, "goals_v1")
        defaults.synchronize()
    }
}
