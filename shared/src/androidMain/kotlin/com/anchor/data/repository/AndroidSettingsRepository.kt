package com.anchor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.anchor.domain.model.Sensitivity
import com.anchor.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalTime
import com.anchor.platform.AndroidContextHolder

class AndroidSettingsRepository(
    context: Context = AndroidContextHolder.application
) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("anchor_settings", Context.MODE_PRIVATE)

    private val _sensitivity = MutableStateFlow(
        runCatching { Sensitivity.valueOf(prefs.getString("sensitivity", "Medium") ?: "Medium") }
            .getOrDefault(Sensitivity.Medium)
    )
    private val _lock = MutableStateFlow(prefs.getBoolean("lock_mode", false))
    private val _notif = MutableStateFlow(prefs.getBoolean("notifications", false))
    private val _target = MutableStateFlow(prefs.getInt("daily_target", 5))
    private val _minSessionDuration = MutableStateFlow(prefs.getInt("min_session_duration", 5))
    private val _interventionLevel = MutableStateFlow(
        runCatching { com.anchor.domain.model.InterventionLevel.valueOf(prefs.getString("intervention_level", "Medium") ?: "Medium") }
            .getOrDefault(com.anchor.domain.model.InterventionLevel.Medium)
    )
    private val _onboarding = MutableStateFlow(prefs.getBoolean("onboarding_completed", false))
    private val _blocked = MutableStateFlow(prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet())
    private val _blockAfterMinutes = MutableStateFlow(prefs.getInt("block_after_minutes", 0))
    private val _reminders = MutableStateFlow(loadReminders())

    override val sensitivity = _sensitivity.asStateFlow()
    override val lockMode = _lock.asStateFlow()
    override val notifications = _notif.asStateFlow()
    override val dailyTarget = _target.asStateFlow()
    override val minSessionDuration = _minSessionDuration.asStateFlow()
    override val interventionLevel = _interventionLevel.asStateFlow()
    override val blockedApps = _blocked.asStateFlow()
    override val blockAfterMinutes = _blockAfterMinutes.asStateFlow()
    override val onboardingCompleted = _onboarding.asStateFlow()
    override val reminders = _reminders.asStateFlow()

    override suspend fun setSensitivity(s: Sensitivity) {
        _sensitivity.value = s
        prefs.edit().putString("sensitivity", s.name).apply()
    }

    override suspend fun setLockMode(enabled: Boolean) {
        _lock.value = enabled
        prefs.edit().putBoolean("lock_mode", enabled).apply()
    }

    override suspend fun setNotifications(enabled: Boolean) {
        _notif.value = enabled
        prefs.edit().putBoolean("notifications", enabled).apply()
    }

    override suspend fun setDailyTarget(n: Int) {
        _target.value = n
        prefs.edit().putInt("daily_target", n).apply()
    }

    override suspend fun setMinSessionDuration(minutes: Int) {
        _minSessionDuration.value = minutes
        prefs.edit().putInt("min_session_duration", minutes).apply()
    }

    override suspend fun setInterventionLevel(level: com.anchor.domain.model.InterventionLevel) {
        _interventionLevel.value = level
        prefs.edit().putString("intervention_level", level.name).apply()
    }

    override suspend fun setBlockedApps(apps: Set<String>) {
        _blocked.value = apps
        prefs.edit().putStringSet("blocked_apps", apps).apply()
    }

    override suspend fun setBlockAfterMinutes(minutes: Int) {
        _blockAfterMinutes.value = minutes
        prefs.edit().putInt("block_after_minutes", minutes).apply()
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        _onboarding.value = completed
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
    }

    override suspend fun addReminder(time: LocalTime) {
        val newReminders = (_reminders.value + time).distinct().sorted()
        _reminders.value = newReminders
        saveReminders(newReminders)
    }

    override suspend fun removeReminder(time: LocalTime) {
        val newReminders = _reminders.value - time
        _reminders.value = newReminders
        saveReminders(newReminders)
    }

    private fun loadReminders(): List<LocalTime> {
        val raw = prefs.getStringSet("reminders", emptySet()) ?: emptySet()
        return raw.map { 
            val parts = it.split(":")
            LocalTime(parts[0].toInt(), parts[1].toInt())
        }.sorted()
    }

    private fun saveReminders(times: List<LocalTime>) {
        val raw = times.map { "${it.hour}:${it.minute}" }.toSet()
        prefs.edit().putStringSet("reminders", raw).apply()
    }
}
