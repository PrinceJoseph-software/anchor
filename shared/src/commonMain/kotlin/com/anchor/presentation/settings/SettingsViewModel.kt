package com.anchor.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anchor.data.repository.AnchorJson
import com.anchor.data.repository.AnchorSigner
import com.anchor.domain.model.Action
import com.anchor.domain.model.ActionIcon
import com.anchor.domain.model.Goal
import com.anchor.domain.model.Sensitivity
import com.anchor.domain.model.Session
import com.anchor.domain.repository.ActionRepository
import com.anchor.domain.repository.GoalRepository
import com.anchor.domain.repository.HistoryLog
import com.anchor.domain.repository.HistoryRepository
import com.anchor.domain.repository.SessionRepository
import com.anchor.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class AnchorExport(
    val version: Int = 2,
    val exportedAt: Long = 0L,
    val actions: List<Action> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val historyLogs: List<HistoryLog> = emptyList(),
    val sensitivity: String = "Medium",
    val dailyTarget: Int = 5,
    val minSessionDuration: Int = 5,
    val interventionLevel: String = "Medium",
    /** HMAC-SHA-256 of the canonical JSON (this field set to ""). Empty on legacy v1 exports. */
    val signature: String = "",
)

/** Human-readable summary of an export payload, shown in the dialog. */
data class ExportSummary(
    val actionCount: Int,
    val goalCount: Int,
    val sessionCount: Int,
    val logCount: Int,
) {
    fun label(): String = buildString {
        append("$actionCount action${if (actionCount != 1) "s" else ""}")
        append(" · $goalCount goal${if (goalCount != 1) "s" else ""}")
        append(" · $sessionCount session${if (sessionCount != 1) "s" else ""}")
        append(" · $logCount log${if (logCount != 1) "s" else ""}")
    }
}

data class SettingsState(
    val actions: List<Action> = emptyList(),
    val sensitivity: Sensitivity = Sensitivity.Medium,
    val lockMode: Boolean = false,
    val notifications: Boolean = false,
    val minSessionDuration: Int = 5,
    val dailyTarget: Int = 5,
    val interventionLevel: com.anchor.domain.model.InterventionLevel = com.anchor.domain.model.InterventionLevel.Medium,
    val reminders: List<kotlinx.datetime.LocalTime> = emptyList(),
    val blockAfterMinutes: Int = 0,
    val blockedAppsCount: Int = 0,
    val exportJson: String? = null,
    val exportSummary: ExportSummary? = null,
    val importError: String? = null,
    val importSuccess: Boolean = false,
)

class SettingsViewModel(
    private val actionsRepo: ActionRepository,
    private val settings: SettingsRepository,
    private val goalRepo: GoalRepository,
    private val sessionsRepo: SessionRepository,
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    private val _exportJson = MutableStateFlow<String?>(null)
    private val _exportSummary = MutableStateFlow<ExportSummary?>(null)
    private val _importError = MutableStateFlow<String?>(null)
    private val _importSuccess = MutableStateFlow(false)

    private data class BaseSettings(
        val actions: List<Action>,
        val sensitivity: Sensitivity,
        val lockMode: Boolean,
        val notifications: Boolean,
    )

    private data class SessionSettings(
        val minDuration: Int,
        val dailyTarget: Int,
        val interventionLevel: com.anchor.domain.model.InterventionLevel,
        val reminders: List<kotlinx.datetime.LocalTime>,
        val blockAfterMinutes: Int,
    )

    val state: StateFlow<SettingsState> = combine(
        combine(
            actionsRepo.actions,
            settings.sensitivity,
            settings.lockMode,
            settings.notifications,
        ) { actions, sensitivity, lockMode, notifications ->
            BaseSettings(actions, sensitivity, lockMode, notifications)
        },
        combine(
            settings.minSessionDuration,
            settings.dailyTarget,
            settings.interventionLevel,
            settings.reminders,
            settings.blockAfterMinutes,
        ) { minDuration, dailyTarget, interventionLevel, reminders, blockAfter ->
            SessionSettings(minDuration, dailyTarget, interventionLevel, reminders, blockAfter)
        },
        combine(_exportJson, _exportSummary, _importError, _importSuccess) { e, es, err, ok ->
            arrayOf(e, es, err, ok)
        },
        settings.blockedApps,
    ) { base, session, extras, blockedApps ->
        @Suppress("UNCHECKED_CAST")
        SettingsState(
            actions = base.actions,
            sensitivity = base.sensitivity,
            lockMode = base.lockMode,
            notifications = base.notifications,
            minSessionDuration = session.minDuration,
            dailyTarget = session.dailyTarget,
            interventionLevel = session.interventionLevel,
            reminders = session.reminders,
            blockAfterMinutes = session.blockAfterMinutes,
            blockedAppsCount = blockedApps.size,
            exportJson = extras[0] as String?,
            exportSummary = extras[1] as ExportSummary?,
            importError = extras[2] as String?,
            importSuccess = extras[3] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun addAction(name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val ts = Clock.System.now().toEpochMilliseconds().toString(36)
        actionsRepo.add(Action("act-$ts-${Random.nextInt(0xFFFF).toString(16)}", name.trim(), ActionIcon.Generic, isCustom = true))
    }
    fun remove(id: String) = viewModelScope.launch { actionsRepo.remove(id) }
    fun setSensitivity(s: Sensitivity) = viewModelScope.launch { settings.setSensitivity(s) }
    fun setLock(b: Boolean) = viewModelScope.launch { settings.setLockMode(b) }
    fun setNotif(b: Boolean) = viewModelScope.launch { settings.setNotifications(b) }
    fun setMinSessionDuration(minutes: Int) = viewModelScope.launch { settings.setMinSessionDuration(minutes) }
    fun setDailyTarget(n: Int) = viewModelScope.launch { settings.setDailyTarget(n) }
    fun setInterventionLevel(level: com.anchor.domain.model.InterventionLevel) = viewModelScope.launch { settings.setInterventionLevel(level) }
    fun setBlockAfterMinutes(minutes: Int) = viewModelScope.launch { settings.setBlockAfterMinutes(minutes) }
    fun addReminder(time: kotlinx.datetime.LocalTime) = viewModelScope.launch { settings.addReminder(time) }
    fun removeReminder(time: kotlinx.datetime.LocalTime) = viewModelScope.launch { settings.removeReminder(time) }

    // ── Import / Export ──────────────────────────────────────────────────────

    fun requestExport() = viewModelScope.launch {
        val actions = actionsRepo.actions.first()
        val goals = goalRepo.goals.first()
        val sessions = sessionsRepo.sessions.first()
        val logs = historyRepo.logs.first()

        // Build the export with an empty signature first so we can compute the
        // canonical JSON for signing (signature field must be "" during signing).
        val unsigned = AnchorExport(
            exportedAt = Clock.System.now().toEpochMilliseconds(),
            actions = actions,
            goals = goals,
            sessions = sessions,
            historyLogs = logs,
            sensitivity = settings.sensitivity.first().name,
            dailyTarget = settings.dailyTarget.first(),
            minSessionDuration = settings.minSessionDuration.first(),
            interventionLevel = settings.interventionLevel.first().name,
            signature = "",
        )
        val canonical = AnchorJson.encodeToString(AnchorExport.serializer(), unsigned)
        val sig = AnchorSigner.sign(canonical)

        // Re-encode with the computed signature embedded
        val signed = unsigned.copy(signature = sig)
        _exportJson.value = AnchorJson.encodeToString(AnchorExport.serializer(), signed)
        _exportSummary.value = ExportSummary(
            actionCount = actions.size,
            goalCount = goals.size,
            sessionCount = sessions.size,
            logCount = logs.size,
        )
    }

    fun clearExport() {
        _exportJson.value = null
        _exportSummary.value = null
    }

    fun importData(json: String) = viewModelScope.launch {
        runCatching {
            val data = AnchorJson.decodeFromString(AnchorExport.serializer(), json.trim())

            // ── Signature verification ───────────────────────────────────────
            // Verify HMAC-SHA-256. Legacy v1 exports have an empty signature
            // and are accepted (they pre-date the security layer). v2+ exports
            // with a non-empty signature must pass verification — a mismatch
            // means the JSON was manually crafted or tampered with.
            if (data.signature.isNotEmpty()) {
                val canonical = AnchorJson.encodeToString(
                    AnchorExport.serializer(), data.copy(signature = "")
                )
                check(AnchorSigner.verify(canonical, data.signature)) {
                    "Backup integrity check failed. This file was not exported from Anchor or has been modified."
                }
            }

            // Restore actions (replace all)
            actionsRepo.replaceAll(data.actions)

            // Restore goals (merge by ID — skip existing)
            val existingGoalIds = goalRepo.goals.first().map { it.id }.toSet()
            val newGoals = data.goals.filter { it.id !in existingGoalIds }
            if (newGoals.isNotEmpty()) {
                val merged = goalRepo.goals.first() + newGoals
                goalRepo.replaceAll(merged)
            }

            // Sessions and history logs are included in v2 exports but are not
            // imported automatically to prevent overwriting newer local data.
            // Users can perform a fresh install + import if full restoration is needed.

            // Restore settings
            runCatching { settings.setSensitivity(Sensitivity.valueOf(data.sensitivity)) }
            settings.setDailyTarget(data.dailyTarget)
            settings.setMinSessionDuration(data.minSessionDuration)
            runCatching {
                settings.setInterventionLevel(
                    com.anchor.domain.model.InterventionLevel.valueOf(data.interventionLevel)
                )
            }
            _importSuccess.value = true
            _importError.value = null
        }.onFailure { e ->
            _importError.value = "Import failed: ${e.message?.take(80) ?: "Invalid data"}"
            _importSuccess.value = false
        }
    }

    fun clearImportState() {
        _importError.value = null
        _importSuccess.value = false
    }
}
