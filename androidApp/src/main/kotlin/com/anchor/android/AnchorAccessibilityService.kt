package com.anchor.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.anchor.domain.model.InterventionLevel
import com.anchor.domain.model.SessionOutcome
import com.anchor.domain.repository.SessionRepository
import com.anchor.domain.repository.SettingsRepository
import com.anchor.platform.Interrupter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar

class AnchorAccessibilityService : AccessibilityService(), KoinComponent {

    private val settings: SettingsRepository by inject()
    private val sessionsRepo: SessionRepository by inject()
    private val interrupter: Interrupter by inject()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            if (packageName == this.packageName) return
            
            scope.launch {
                val isLockMode = settings.lockMode.first()
                if (!isLockMode) return@launch

                val blockedApps = settings.blockedApps.first()
                if (!blockedApps.contains(packageName)) return@launch

                // If a focus session is currently active, block all listed apps immediately —
                // the user should not be in a distracting app while a session is running.
                val sessionActive = sessionsRepo.sessions.first()
                    .any { it.outcome == SessionOutcome.InProgress }

                if (!sessionActive) {
                    // No active session: apply the daily time-limit check instead.
                    val blockAfterMinutes = settings.blockAfterMinutes.first()
                    if (blockAfterMinutes > 0) {
                        // Compare in milliseconds to avoid integer-division truncation
                        // (e.g. 59 seconds of usage would floor to 0 minutes, bypassing a 1-min limit).
                        val usedMs = todayUsageMillis(packageName)
                        val blockAfterMs = blockAfterMinutes * 60_000L
                        if (usedMs < blockAfterMs) return@launch
                    }
                }

                val level = settings.interventionLevel.first()
                // Low = notification only; Medium/High = allow overlay takeover
                val forceNotification = level == InterventionLevel.Low
                interrupter.interrupt(
                    title = "Distraction Detected",
                    body = "You tried to open a blocked app. Start a productive session instead.",
                    deepLink = "intervention://drift",
                    forceNotification = forceNotification,
                )
            }
        }
    }

    /**
     * Returns how many milliseconds [packageName] has been used in the foreground today.
     * Uses the cached [UsageStatsManager.queryUsageStats] API. Accurate to ~30 s.
     */
    private fun todayUsageMillis(packageName: String): Long {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
        return stats?.firstOrNull { it.packageName == packageName }?.totalTimeInForeground ?: 0L
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
