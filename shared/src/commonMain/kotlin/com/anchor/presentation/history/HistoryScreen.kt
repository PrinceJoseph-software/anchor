package com.anchor.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.domain.repository.HistoryLog
import com.anchor.domain.repository.LogType
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorSpacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    vm: HistoryViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(AnchorSpacing.xl))
        Row(
            Modifier.padding(horizontal = AnchorSpacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AnchorColors.OnBg,
                )
            }
            Text(
                "Activity Logs",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AnchorColors.OnBg,
            )
        }
        Spacer(Modifier.height(AnchorSpacing.l))

        if (state.logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = AnchorColors.OnBgMuted,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(AnchorSpacing.m))
                    Text("No activity yet", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(AnchorSpacing.s))
                    Text(
                        "Your sessions and decisions will appear here.",
                        color = AnchorColors.OnBgMuted,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = AnchorSpacing.xl,
                    vertical = AnchorSpacing.m,
                ),
                verticalArrangement = Arrangement.spacedBy(AnchorSpacing.s),
            ) {
                // Group logs by calendar date
                val grouped = state.logs.groupBy { log ->
                    val ldt = Instant.fromEpochMilliseconds(log.timestamp)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                    "${ldt.year}-${ldt.monthNumber.toString().padStart(2, '0')}-${ldt.dayOfMonth.toString().padStart(2, '0')}"
                }.entries.sortedByDescending { it.key }

                grouped.forEach { (dateKey, logsForDay) ->
                    item(key = "header-$dateKey") {
                        val parts = dateKey.split("-")
                        val month = monthName(parts[1].toInt())
                        val day = parts[2].toInt()
                        val year = parts[0].toInt()
                        val currentYear = kotlinx.datetime.Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault()).year
                        val label = if (year == currentYear) "$month $day" else "$month $day, $year"
                        Text(
                            label,
                            color = AnchorColors.OnBgMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = AnchorSpacing.m, bottom = AnchorSpacing.xs),
                        )
                    }
                    items(logsForDay, key = { it.timestamp }) { log ->
                        LogItem(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItem(log: HistoryLog) {
    val ldt = Instant.fromEpochMilliseconds(log.timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val timeStr = "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"

    val (title, color) = when (log.type) {
        LogType.SESSION_COMPLETE -> "Completed" to AnchorColors.Gold
        LogType.SESSION_SKIPPED -> "Skipped" to AnchorColors.OnBgMuted
        LogType.SESSION_ABANDONED -> "Abandoned" to AnchorColors.Danger
        LogType.STREAK_RESET -> "Streak Reset" to Color(0xFFFF6B6B)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(AnchorColors.Surface, RoundedCornerShape(16.dp))
            .padding(AnchorSpacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(AnchorSpacing.s))
            Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(timeStr, color = AnchorColors.OnBgMuted, fontSize = 12.sp)
        }
        if (!log.actionName.isNullOrBlank()) {
            Spacer(Modifier.height(AnchorSpacing.xs))
            Text(log.actionName, color = AnchorColors.OnBg, fontSize = 14.sp)
        }
        if (!log.reason.isNullOrBlank()) {
            Spacer(Modifier.height(AnchorSpacing.xs))
            Text(
                "\"${log.reason}\"",
                color = AnchorColors.OnBgMuted,
                fontSize = 13.sp,
            )
        }
    }
}

private fun monthName(monthNumber: Int): String = when (monthNumber) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
    5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
    9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
    else -> "?"
}
