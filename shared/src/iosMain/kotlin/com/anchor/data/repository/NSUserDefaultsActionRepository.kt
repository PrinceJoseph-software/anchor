package com.anchor.data.repository

import com.anchor.domain.model.Action
import com.anchor.domain.repository.ActionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import platform.Foundation.NSUserDefaults

class NSUserDefaultsActionRepository : ActionRepository {

    private val defaults = NSUserDefaults.standardUserDefaults
    private val state = MutableStateFlow<List<Action>>(loadActions())
    override val actions: StateFlow<List<Action>> = state.asStateFlow()

    override suspend fun add(action: Action) {
        state.update { it + action }
        saveActions(state.value)
    }

    override suspend fun remove(id: String) {
        state.update { it.filterNot { a -> a.id == id } }
        saveActions(state.value)
    }

    override suspend fun replaceAll(actions: List<Action>) {
        state.value = actions
        saveActions(actions)
    }

    private fun loadActions(): List<Action> = runCatching {
        val json = defaults.stringForKey("actions_list_v2") ?: return emptyList()
        AnchorJson.decodeFromString(ListSerializer(Action.serializer()), json)
    }.getOrDefault(emptyList())

    private fun saveActions(actions: List<Action>) {
        val json = AnchorJson.encodeToString(ListSerializer(Action.serializer()), actions)
        defaults.setObject(json, "actions_list_v2")
        defaults.synchronize()
    }
}
