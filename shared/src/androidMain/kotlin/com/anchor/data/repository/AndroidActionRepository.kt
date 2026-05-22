package com.anchor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.anchor.domain.model.Action
import com.anchor.domain.model.ActionIcon
import com.anchor.domain.repository.ActionRepository
import com.anchor.platform.AndroidContextHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer

class AndroidActionRepository(
    context: Context = AndroidContextHolder.application,
) : ActionRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anchor_actions", Context.MODE_PRIVATE)
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
        val json = prefs.getString("actions_list_v2", null)
            ?: return migrateLegacy()
        AnchorJson.decodeFromString(ListSerializer(Action.serializer()), json)
    }.getOrDefault(emptyList())

    private fun saveActions(actions: List<Action>) {
        val json = AnchorJson.encodeToString(ListSerializer(Action.serializer()), actions)
        prefs.edit().putString("actions_list_v2", json).apply()
    }

    private fun migrateLegacy(): List<Action> {
        val raw = prefs.getString("actions_list", null) ?: return emptyList()
        if (raw.isBlank() || raw == "[]") return emptyList()
        return raw.split("|").mapNotNull { part ->
            val cols = part.split("::")
            if (cols.size >= 4) runCatching {
                Action(
                    id = cols[0],
                    name = cols[1],
                    icon = runCatching { ActionIcon.valueOf(cols[2]) }.getOrDefault(ActionIcon.Generic),
                    isCustom = cols[3].toBoolean(),
                )
            }.getOrNull() else null
        }.also { migrated ->
            saveActions(migrated)
            prefs.edit().remove("actions_list").apply()
        }
    }
}
