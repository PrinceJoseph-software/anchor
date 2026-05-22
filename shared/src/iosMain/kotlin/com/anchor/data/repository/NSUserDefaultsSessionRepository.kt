package com.anchor.data.repository

import com.anchor.domain.model.Session
import com.anchor.domain.model.SessionOutcome
import com.anchor.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import platform.Foundation.NSUserDefaults
import kotlin.random.Random

class NSUserDefaultsSessionRepository(
    private val clock: Clock = Clock.System,
) : SessionRepository {

    private val defaults = NSUserDefaults.standardUserDefaults
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
        val json = defaults.stringForKey("sessions_list_v2") ?: return emptyList()
        AnchorJson.decodeFromString(ListSerializer(Session.serializer()), json)
    }.getOrDefault(emptyList())

    private fun saveSessions(sessions: List<Session>) {
        val json = AnchorJson.encodeToString(ListSerializer(Session.serializer()), sessions)
        defaults.setObject(json, "sessions_list_v2")
        defaults.synchronize()
    }
}
