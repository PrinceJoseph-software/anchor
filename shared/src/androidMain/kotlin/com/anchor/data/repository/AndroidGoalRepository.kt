package com.anchor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.anchor.domain.model.Goal
import com.anchor.domain.model.GoalStatus
import com.anchor.domain.repository.GoalRepository
import com.anchor.platform.AndroidContextHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock

class AndroidGoalRepository(
    context: Context = AndroidContextHolder.application,
) : GoalRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anchor_goals", Context.MODE_PRIVATE)

    private val _goals = MutableStateFlow<List<Goal>>(loadGoals())
    private val _exemptionCards = MutableStateFlow(prefs.getInt("exemption_cards", 0))

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
        prefs.edit().putInt("exemption_cards", _exemptionCards.value).apply()
    }

    override suspend fun useExemptionCard(): Boolean {
        val cur = _exemptionCards.value
        return if (cur > 0) {
            _exemptionCards.value = cur - 1
            prefs.edit().putInt("exemption_cards", _exemptionCards.value).apply()
            true
        } else false
    }

    override suspend fun replaceAll(goals: List<Goal>) {
        _goals.value = goals
        persist()
    }

    private fun loadGoals(): List<Goal> {
        val raw = prefs.getString("goals_v1", null) ?: return emptyList()
        return runCatching { AnchorJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Goal.serializer()), raw) }.getOrDefault(emptyList())
    }

    private fun persist() {
        prefs.edit()
            .putString("goals_v1", AnchorJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Goal.serializer()), _goals.value))
            .apply()
    }
}
