package com.anchor.data.repository

import com.anchor.domain.model.InterventionLevel
import com.anchor.domain.model.Sensitivity
import com.anchor.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import platform.Foundation.NSUserDefaults

/**
 * Persists settings to NSUserDefaults on iOS.
 */
class NSUserDefaultsSettingsRepository : SettingsRepository {

    private val defaults = NSUserDefaults.standardUserDefaults

    private fun string(key: String, fallback: String): String =
        defaults.stringForKey(key) ?: fallback

    private fun bool(key: String, fallback: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) fallback else defaults.boolForKey(key)

    private fun int(key: String, fallback: Int): Int =
        if (defaults.objectForKey(key) == null) fallback else defaults.integerForKey(key).toInt()

    // ---- backing state flows ------------------------------------------------

    private val _sensitivity = MutableStateFlow(
        runCatching { Sensitivity.valueOf(string("sensitivity", "Medium")) }.getOrDefault(Sensitivity.Medium)
    )
    private val _lock = MutableStateFlow(bool("lock_mode", false))
    private val _notif = MutableStateFlow(bool("notifications", false))
    private val _target = MutableStateFlow(int("daily_target", 5))
    private val _minDuration = MutableStateFlow(int("min_session_duration", 5))
    private val _level = MutableStateFlow(
        runCatching { InterventionLevel.valueOf(string("intervention_level", "Medium")) }.getOrDefault(InterventionLevel.Medium)
    )
    private val _onboarding = MutableStateFlow(bool("onboarding_completed", false))
    private val _blocked = MutableStateFlow<Set<String>>(loadBlocked())
    private val _blockAfterMinutes = MutableStateFlow(int("block_after_minutes", 0))
    private val _reminders = MutableStateFlow<List<LocalTime>>(loadReminders())

    // ---- public flows -------------------------------------------------------

    override val sensitivity: StateFlow<Sensitivity> = _sensitivity.asStateFlow()
    override val lockMode: StateFlow<Boolean> = _lock.asStateFlow()
    override val notifications: StateFlow<Boolean> = _notif.asStateFlow()
    override val dailyTarget: StateFlow<Int> = _target.asStateFlow()
    override val minSessionDuration: StateFlow<Int> = _minDuration.asStateFlow()
    override val interventionLevel: StateFlow<InterventionLevel> = _level.asStateFlow()
    override val blockedApps: StateFlow<Set<String>> = _blocked.asStateFlow()
    override val blockAfterMinutes: StateFlow<Int> = _blockAfterMinutes.asStateFlow()
    override val reminders: StateFlow<List<LocalTime>> = _reminders.asStateFlow()
    override val onboardingCompleted: StateFlow<Boolean> = _onboarding.asStateFlow()

    // ---- mutators -----------------------------------------------------------

    override suspend fun setSensitivity(s: Sensitivity) {
        _sensitivity.value = s; defaults.setObject(s.name, "sensitivity"); defaults.synchronize()
    }

    override suspend fun setLockMode(enabled: Boolean) {
        _lock.value = enabled; defaults.setBool(enabled, "lock_mode"); defaults.synchronize()
    }

    override suspend fun setNotifications(enabled: Boolean) {
        _notif.value = enabled; defaults.setBool(enabled, "notifications"); defaults.synchronize()
    }

    override suspend fun setDailyTarget(n: Int) {
        _target.value = n; defaults.setInteger(n.toLong(), "daily_target"); defaults.synchronize()
    }

    override suspend fun setMinSessionDuration(minutes: Int) {
        _minDuration.value = minutes
        defaults.setInteger(minutes.toLong(), "min_session_duration")
        defaults.synchronize()
    }

    override suspend fun setInterventionLevel(level: InterventionLevel) {
        _level.value = level; defaults.setObject(level.name, "intervention_level"); defaults.synchronize()
    }

    override suspend fun setBlockedApps(apps: Set<String>) {
        _blocked.value = apps
        val json = AnchorJson.encodeToString(SetSerializer(String.serializer()), apps)
        defaults.setObject(json, "blocked_apps_v2")
        defaults.synchronize()
    }

    override suspend fun setBlockAfterMinutes(minutes: Int) {
        _blockAfterMinutes.value = minutes
        defaults.setInteger(minutes.toLong(), "block_after_minutes")
        defaults.synchronize()
    }

    override suspend fun addReminder(time: LocalTime) {
        val updated = (_reminders.value + time).distinct().sorted()
        _reminders.value = updated
        saveReminders(updated)
    }

    override suspend fun removeReminder(time: LocalTime) {
        val updated = _reminders.value - time
        _reminders.value = updated
        saveReminders(updated)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        _onboarding.value = completed
        defaults.setBool(completed, "onboarding_completed")
        defaults.synchronize()
    }

    // ---- helpers ------------------------------------------------------------

    private fun loadBlocked(): Set<String> = runCatching {
        val json = defaults.stringForKey("blocked_apps_v2") ?: return emptySet()
        AnchorJson.decodeFromString(SetSerializer(String.serializer()), json)
    }.getOrDefault(emptySet())

    private fun loadReminders(): List<LocalTime> = runCatching {
        val raw = defaults.stringForKey("reminders") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        raw.split("|").mapNotNull { part ->
            val cols = part.split(":")
            if (cols.size >= 2) runCatching { LocalTime(cols[0].toInt(), cols[1].toInt()) }.getOrNull()
            else null
        }.sorted()
    }.getOrDefault(emptyList())

    private fun saveReminders(times: List<LocalTime>) {
        val raw = times.joinToString("|") { "${it.hour}:${it.minute}" }
        defaults.setObject(raw, "reminders")
        defaults.synchronize()
    }
}
