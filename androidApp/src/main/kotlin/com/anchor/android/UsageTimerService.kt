package com.anchor.android

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.anchor.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Calendar

/**
 * Foreground service that accurately tracks daily foreground usage for blocked apps.
 *
 * Uses [UsageEvents] (not the cached [queryUsageStats]) so readings are real-time —
 * accounting for an app that is open right now.  Polls every 30 s.
 *
 * A PARTIAL_WAKE_LOCK prevents the CPU from entering deep sleep between polls,
 * ensuring the loop keeps ticking even when the screen is off and the user is
 * away from the Anchor app.
 */
class UsageTimerService : Service() {

    private val settings: SettingsRepository by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "anchor_usage_timer"
        private const val NOTIFICATION_ID = 2001
        private const val POLL_INTERVAL_MS = 30_000L

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, UsageTimerService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, UsageTimerService::class.java))
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring app usage…"))

        // Keep the CPU awake so our coroutine loop isn't suspended when screen-off.
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Anchor::UsageTimerWakeLock",
        ).apply { acquire(12 * 60 * 60 * 1_000L) } // auto-release after 12 h at most

        scope.launch { monitorLoop() }
        return START_STICKY   // restart automatically if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    // ── Monitor loop ──────────────────────────────────────────────────────────

    private suspend fun monitorLoop() {
        while (true) {
            val lockOn = settings.lockMode.first()
            val limitMinutes = settings.blockAfterMinutes.first()

            // Self-stop when no longer needed
            if (!lockOn || limitMinutes <= 0) {
                stopSelf()
                return
            }

            val blockedApps = settings.blockedApps.first()
            val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager

            if (usm != null && blockedApps.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val startOfDay = startOfDayMs()

                // Real-time query via events (not cached queryUsageStats)
                val usedMinutes = queryForegroundMinutes(usm, blockedApps, startOfDay, now)

                // Find a friendly label for the most-used blocked app
                val appLabel = blockedApps.maxByOrNull { pkg ->
                    queryForegroundMinutes(usm, setOf(pkg), startOfDay, now)
                }?.let { pkg ->
                    runCatching {
                        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                    }.getOrElse { pkg }
                } ?: "${blockedApps.size} app${if (blockedApps.size != 1) "s" else ""}"

                val text = when {
                    usedMinutes <= 0 ->
                        "Monitoring $appLabel"
                    usedMinutes >= limitMinutes ->
                        "${fmt(usedMinutes.toLong())} used — ${fmt(limitMinutes.toLong())} limit reached"
                    else ->
                        "${fmt(usedMinutes.toLong())} of ${fmt(limitMinutes.toLong())} daily limit used"
                }

                updateNotification(text)
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    // ── Real-time usage query ─────────────────────────────────────────────────

    /**
     * Computes total foreground minutes for [packages] from [startMs] to [endMs] by
     * replaying raw [UsageEvents].  This is accurate to the current moment because
     * any app that is still in the foreground at [endMs] has its ongoing segment
     * included automatically.
     */
    private fun queryForegroundMinutes(
        usm: UsageStatsManager,
        packages: Set<String>,
        startMs: Long,
        endMs: Long,
    ): Long {
        val events = usm.queryEvents(startMs, endMs) ?: return 0L
        val event = UsageEvents.Event()
        // last MOVE_TO_FOREGROUND timestamp per package
        val fgStart = mutableMapOf<String, Long>()
        var totalMs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName !in packages) continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND ->
                    fgStart[event.packageName] = event.timeStamp

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    fgStart.remove(event.packageName)?.let { start ->
                        totalMs += event.timeStamp - start
                    }
                }
            }
        }

        // App(s) still in the foreground right now — count time up to endMs
        for ((_, start) in fgStart) {
            totalMs += endMs - start
        }

        return totalMs / 60_000L
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun startOfDayMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Format minutes as "Xm", "Xh", or "Xh Ym". */
    private fun fmt(minutes: Long): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0L -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Usage Timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background usage monitoring for blocked apps."
                setShowBadge(false)
                setSound(null, null)
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = tapIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Anchor — Usage Monitor")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun immutableFlag() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
