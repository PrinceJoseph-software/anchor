package com.anchor.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.anchor.domain.repository.SettingsRepository
import com.anchor.platform.Interrupter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class ReminderReceiver : BroadcastReceiver(), KoinComponent {
    private val interrupter: Interrupter by inject()

    override fun onReceive(context: Context, intent: Intent) {
        // Fire the intervention notification immediately.
        interrupter.interrupt(
            title = "Anchor Reminder",
            body = "It's time to anchor yourself. Start a productive session.",
            deepLink = "intervention://reminder",
            forceNotification = true,
        )

        // Reschedule for the same time tomorrow — AlarmManager alarms are one-shot
        // and must be explicitly re-set after firing or they will never fire again.
        val hour   = intent.getIntExtra(EXTRA_HOUR,   -1)
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
        if (hour >= 0 && minute >= 0) {
            ReminderScheduler(context).scheduleNextDay(LocalTime(hour, minute))
        }
    }

    companion object {
        const val EXTRA_HOUR   = "anchor.reminder.hour"
        const val EXTRA_MINUTE = "anchor.reminder.minute"
    }
}

class BootReceiver : BroadcastReceiver(), KoinComponent {
    private val settings: SettingsRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val scheduler = ReminderScheduler(context)
                val reminders = settings.reminders.first()
                reminders.forEach { scheduler.scheduleOne(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ReminderScheduler(private val context: Context) : KoinComponent {
    private val settings: SettingsRepository by inject()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Currently scheduled reminder times; used by cancelAll() to cancel exact alarms. */
    private var scheduledTimes: List<LocalTime> = emptyList()

    /** Starts collecting reminders from settings and keeps alarms in sync. */
    fun start() {
        scope.launch {
            settings.reminders.collect { reminders ->
                cancelAll()
                reminders.forEach { scheduleOne(it) }
            }
        }
    }

    /** One-shot reschedule; used by BootReceiver where collection isn't needed. */
    fun scheduleOne(time: LocalTime) = schedule(time, advanceIfPast = true)

    /**
     * Schedules the alarm for exactly one day from now at [time].
     * Called by [ReminderReceiver] after each alarm fires so it repeats daily.
     */
    fun scheduleNextDay(time: LocalTime) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)         // always tomorrow — alarm just fired today
        }
        val pi = PendingIntent.getBroadcast(
            context, time.hashCode(), buildIntent(time),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(cal.timeInMillis, pi)
    }

    private fun schedule(time: LocalTime, advanceIfPast: Boolean) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (advanceIfPast && timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val pi = PendingIntent.getBroadcast(
            context, time.hashCode(), buildIntent(time),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(calendar.timeInMillis, pi)
        scheduledTimes = scheduledTimes + time
    }

    /** Builds a reminder intent that carries the hour/minute for rescheduling on receipt. */
    private fun buildIntent(time: LocalTime): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_HOUR,   time.hour)
            putExtra(ReminderReceiver.EXTRA_MINUTE, time.minute)
        }

    private fun setAlarm(triggerAtMs: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // No SCHEDULE_EXACT_ALARM permission granted; fall back to inexact wakeup alarm.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }

    private fun cancelAll() {
        scheduledTimes.forEach { time ->
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, time.hashCode(), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
        scheduledTimes = emptyList()
    }
}
