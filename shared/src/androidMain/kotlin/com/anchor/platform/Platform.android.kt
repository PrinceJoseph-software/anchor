package com.anchor.platform

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.NotificationManager
import android.Manifest
import android.app.NotificationChannel
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import android.media.AudioAttributes
private const val ANCHOR_CHANNEL_ID = "anchor_interventions"
private const val ANCHOR_CHANNEL_VERSION = "v3"   // bump to force recreation with correct sound
private const val ANCHOR_NOTIFICATION_ID = 1001
private const val ANCHOR_SOUND_RES = "stranger_things_notif"

actual fun isAndroid(): Boolean = true

actual fun shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "Anchor Backup")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(intent, "Share Anchor Backup").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AndroidContextHolder.application.startActivity(chooser)
}

actual fun hasShownCoachMark(): Boolean =
    AndroidContextHolder.application
        .getSharedPreferences("anchor_meta", android.content.Context.MODE_PRIVATE)
        .getBoolean("coach_mark_shown", false)

actual fun setCoachMarkShown() {
    AndroidContextHolder.application
        .getSharedPreferences("anchor_meta", android.content.Context.MODE_PRIVATE)
        .edit().putBoolean("coach_mark_shown", true).apply()
}

actual class PermissionController(private val context: Context) {
    actual constructor() : this(AndroidContextHolder.application)

    actual fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        else
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
    actual fun requestUsageAccess() {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
    actual fun hasOverlay(): Boolean = Settings.canDrawOverlays(context)
    actual fun requestOverlay() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    actual fun hasAccessibilityService(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as android.view.accessibility.AccessibilityManager
        return am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    actual fun requestAccessibilityService() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    actual fun hasNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.areNotificationsEnabled()
    }
    actual suspend fun requestNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return hasNotifications()
        }
        if (hasNotifications()) {
            return true
        }
        return AndroidContextHolder.requestNotificationPermission() ?: run {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            hasNotifications()
        }
    }
}

actual class Interrupter(private val context: Context) {
    actual constructor() : this(AndroidContextHolder.application)

    actual fun interrupt(title: String, body: String, deepLink: String, forceNotification: Boolean) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("anchor.deepLink", deepLink)
                putExtra("anchor.title", title)
                putExtra("anchor.body", body)
            }
        
        val canOverlay = Settings.canDrawOverlays(context)
        if (canOverlay && !forceNotification) {
            intent?.let { context.startActivity(it) }
            return
        }
        
        if (PermissionController(context).hasNotifications()) {
            postInterventionNotification(title, body, intent)
        } else if (canOverlay || !forceNotification) {
            intent?.let { context.startActivity(it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun postInterventionNotification(title: String, body: String, intent: Intent?) {
        ensureNotificationChannel()
        val pendingIntent = intent?.let {
            PendingIntentFactory.activity(context, it)
        }
        val notification = NotificationCompat.Builder(context, "${ANCHOR_CHANNEL_ID}_${ANCHOR_CHANNEL_VERSION}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(Uri.parse("android.resource://${context.packageName}/raw/${ANCHOR_SOUND_RES}"))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(ANCHOR_NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Use v2 to force recreate with sound if needed
        val channelId = "${ANCHOR_CHANNEL_ID}_${ANCHOR_CHANNEL_VERSION}"
        if (manager.getNotificationChannel(channelId) != null) return
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val soundUri = Uri.parse("android.resource://${context.packageName}/raw/intervention_sound")

        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Anchor interventions",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Decision prompts, delayed interventions, and session reminders."
                setSound(soundUri, audioAttributes)
                enableVibration(true)
            }
        )
    }
}

private object PendingIntentFactory {
    fun activity(context: Context, intent: Intent) =
        android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0
}

actual fun startSessionNotification(actionName: String, startedAt: Long) {
    val ctx = AndroidContextHolder.application
    // Reference by class name string to avoid a circular module dependency
    // (androidApp depends on shared, not the other way around).
    val intent = Intent().setClassName(ctx, "com.anchor.android.SessionTimerService").apply {
        putExtra("anchor.session.actionName", actionName)
        putExtra("anchor.session.startedAt", startedAt)
    }
    ContextCompat.startForegroundService(ctx, intent)
}

actual fun stopSessionNotification() {
    val ctx = AndroidContextHolder.application
    ctx.stopService(Intent().setClassName(ctx, "com.anchor.android.SessionTimerService"))
}

object AndroidContextHolder {
    lateinit var application: Context
    private var notificationRequester: WeakReference<NotificationPermissionRequester>? = null

    fun setNotificationRequester(requester: NotificationPermissionRequester?) {
        notificationRequester = requester?.let { WeakReference(it) }
    }

    suspend fun requestNotificationPermission(): Boolean? =
        notificationRequester?.get()?.requestPostNotifications()
}

interface NotificationPermissionRequester {
    suspend fun requestPostNotifications(): Boolean
}
