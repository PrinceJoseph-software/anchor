package com.anchor.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.anchor.android.MainActivity
import com.anchor.android.R
import com.anchor.domain.usecase.ComputeStreak
import com.anchor.domain.usecase.TodaysProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Streak-focused widget — prominently displays current streak and today's session count.
 */
class AnchorStreakWidgetProvider : AppWidgetProvider(), KoinComponent {

    private val streakUseCase: ComputeStreak by inject()
    private val progressUseCase: TodaysProgress by inject()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        scope.launch {
            val streak = streakUseCase().first()
            val progress = progressUseCase().first()

            val todayText = when {
                progress.completed >= progress.target -> "Target reached today"
                progress.completed == 0 -> "No session yet today"
                else -> "${progress.completed}/${progress.target} sessions today"
            }

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 10, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )

            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_anchor_streak)
                views.setTextViewText(R.id.widget_streak_count, streak.current.toString())
                views.setTextViewText(R.id.widget_streak_today, todayText)
                views.setTextViewText(R.id.widget_streak_btn, "Start Now")
                views.setOnClickPendingIntent(R.id.widget_streak_btn, pendingIntent)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private fun immutableFlag() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
