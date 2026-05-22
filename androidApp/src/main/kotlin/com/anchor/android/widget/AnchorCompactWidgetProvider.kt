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
import com.anchor.domain.usecase.TodaysProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Compact 1×1 widget — shows today's completed sessions. Tap to open Anchor.
 */
class AnchorCompactWidgetProvider : AppWidgetProvider(), KoinComponent {


    private val progressUseCase: TodaysProgress by inject()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        scope.launch {
            val progress = progressUseCase().first()

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 20, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )

            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_anchor_compact)
                views.setTextViewText(R.id.widget_compact_count, progress.completed.toString())
                views.setOnClickPendingIntent(R.id.widget_compact_count, pendingIntent)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private fun immutableFlag() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
