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
 * Primary Anchor widget — shows today's session progress and streak, with a "Start Now" CTA.
 */
class AnchorWidgetProvider : AppWidgetProvider(), KoinComponent {

    private val progressUseCase: TodaysProgress by inject()
    private val streakUseCase: ComputeStreak by inject()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        scope.launch {
            val progress = progressUseCase().first()
            val streak = streakUseCase().first()

            val progressText = when {
                progress.completed >= progress.target -> "Daily target reached"
                progress.completed == 0 -> "Start your first session today"
                else -> "${progress.target - progress.completed} more to reach target"
            }
            val streakText = when {
                streak.current > 0 -> "${streak.current} day streak"
                else -> ""
            }

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )

            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_anchor)
                views.setTextViewText(R.id.widget_progress, progressText)
                views.setTextViewText(R.id.widget_streak, streakText)
                views.setTextViewText(R.id.widget_start_btn, "Start Now")
                views.setOnClickPendingIntent(R.id.widget_start_btn, pendingIntent)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private fun immutableFlag() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
