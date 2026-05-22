package com.anchor.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anchor.domain.model.Action
import com.anchor.domain.model.ActionIcon
import com.anchor.domain.repository.ActionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

data class OnboardingState(
    val available: List<Action> = listOf(
        Action("seed-study", "Study", ActionIcon.Study),
        Action("seed-workout", "Workout", ActionIcon.Workout),
        Action("seed-reading", "Reading", ActionIcon.Reading),
    ),
    val selected: Set<String> = emptySet(),
    val custom: List<Action> = emptyList(),
) {
    val canContinue: Boolean get() = selected.isNotEmpty()
}

class OnboardingViewModel(private val actions: ActionRepository) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun toggle(id: String) = _state.update {
        val sel = it.selected.toMutableSet().apply { if (!add(id)) remove(id) }
        if (sel.size > 5) it else it.copy(selected = sel)
    }

    fun addCustom(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        val ts = Clock.System.now().toEpochMilliseconds().toString(36)
        val a = Action("custom-$ts-${Random.nextInt(0xFFFF).toString(16)}", trimmed, ActionIcon.Generic, isCustom = true)
        _state.update { it.copy(available = it.available + a, custom = it.custom + a) }
    }

    fun commit(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            actions.replaceAll(s.available.filter { it.id in s.selected })
            onDone()
        }
    }
}
