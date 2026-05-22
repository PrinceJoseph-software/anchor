package com.anchor.android

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that shows a persistent "Session in progress" notification
 * while the user has an active focus session, even when the app is backgrounded.
 *
 * The notification ticks the elapsed time every 10 seconds and tapping it brings
 * the user back to the session screen.
 *
 * Started via [start] when a session begins; stopped via [stop] when the session
 * completes or is abandoned.
 */
class SessionTimerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var actionName = "Focus"
    private var startedAtMs = 0L

    companion object {
        private const val CHANNEL_ID = "anchor_session_timer"
        private const val NOTIFICATION_ID = 2002
        const val EXTRA_ACTION_NAME = "anchor.session.actionName"
        const val EXTRA_STARTED_AT  = "anchor.session.startedAt"

        fun start(context: Context, actionName: String, startedAtMs: Long) {
            val intent = Intent(context, SessionTimerService::class.java).apply {
                putExtra(EXTRA_ACTION_NAME, actionName)
                putExtra(EXTRA_STARTED_AT, startedAtMs)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionTimerService::class.java))
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        actionName  = intent?.getStringExtra(EXTRA_ACTION_NAME) ?: "Focus"
        startedAtMs = intent?.getLongExtra(EXTRA_STARTED_AT, 0L) ?: 0L

        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(elapsedSeconds()))

        scope.launch {
            while (true) {
                delay(10_000L)
                updateNotification(elapsedSeconds())
            }
        }

        // Don't restart automatically — the session is over once we stop.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun elapsedSeconds(): Long =
        ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(0)

    private fun formatElapsed(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Session Timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification shown while a focus session is active."
                setShowBadge(false)
                setSound(null, null)
            }
        )
    }

    private fun buildNotification(elapsedSeconds: Long): Notification {
        val tapIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = tapIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Session in progress — $actionName")
            .setContentText("${formatElapsed(elapsedSeconds)} elapsed  •  Tap to return")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(elapsedSeconds: Long) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(elapsedSeconds))
    }

    private fun immutableFlag() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
