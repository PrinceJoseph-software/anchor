package com.anchor.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.domain.model.Goal
import com.anchor.presentation.coachmark.CoachMarkKeys
import com.anchor.presentation.coachmark.coachMarkTarget
import com.anchor.presentation.components.AnchorCard
import com.anchor.presentation.components.HintBox
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorSpacing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatAvgDuration(minutes: Int): String = when {
    minutes <= 0 -> "—"
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun StatsScreen(
    statsVm: StatsViewModel = koinViewModel(),
    goalVm: GoalViewModel = koinViewModel(),
) {
    val s by statsVm.state.collectAsState()
    val g by goalVm.state.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AnchorSpacing.xl, vertical = AnchorSpacing.l),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.l),
    ) {

        // ── Header ───────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Progress",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AnchorColors.OnBg,
            )
            if (s.currentStreak > 0) {
                Surface(
                    color = AnchorColors.GoldMuted,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = AnchorColors.Gold,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            "${s.currentStreak}d streak",
                            color = AnchorColors.Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // ── Follow-Through Rate ───────────────────────────────────────────────
        HintBox("The percentage of sessions you completed out of all sessions started in the last 7 days. A higher rate means you're showing up consistently.") {
            AnchorCard(modifier = Modifier.coachMarkTarget(CoachMarkKeys.STATS_RATE)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingUp, null, tint = AnchorColors.Gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AnchorSpacing.s))
                    Text("Follow-Through Rate", color = AnchorColors.OnBgMuted, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("· 7 days", color = AnchorColors.OnBgMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (s.sample == 0) "—" else "${s.followThroughPct}",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold,
                            color = AnchorColors.OnBg,
                            lineHeight = 54.sp,
                        )
                        if (s.sample > 0) {
                            Text(
                                "%",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AnchorColors.Gold,
                                modifier = Modifier.padding(bottom = 6.dp, start = 3.dp),
                            )
                        }
                    }
                    if (s.sample > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${s.sample}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = AnchorColors.OnBg,
                            )
                            Text("decisions", color = AnchorColors.OnBgMuted, fontSize = 11.sp)
                        }
                    }
                }
                if (s.sample > 0) {
                    Spacer(Modifier.height(AnchorSpacing.s))
                    LinearProgressIndicator(
                        progress = { s.followThroughPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = when {
                            s.followThroughPct >= 80 -> AnchorColors.Gold
                            s.followThroughPct >= 50 -> Color(0xFFCFAA6A)
                            else -> Color(0xFFFF8C42)
                        },
                        trackColor = AnchorColors.Border,
                    )
                }
                Spacer(Modifier.height(AnchorSpacing.xs))
                Text(
                    when {
                        s.sample == 0 -> "Start a session to build your baseline."
                        s.followThroughPct >= 80 -> "Excellent consistency. Keep it up."
                        s.followThroughPct >= 50 -> "Solid progress — keep building."
                        else -> "Every start counts. You've got this."
                    },
                    color = AnchorColors.OnBgMuted,
                    fontSize = 12.sp,
                )
            }
        }

        // ── Quick stats strip ─────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.s),
        ) {
            QuickStat("Total", s.totalSessions.toString(), "sessions", Modifier.weight(1f))
            QuickStat("This Week", s.thisWeek.toString(), "sessions", Modifier.weight(1f))
            QuickStat("Avg Session", formatAvgDuration(s.avgSessionMinutes), "duration", Modifier.weight(1f))
        }

        // ── Weekly Activity ───────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.s)) {
            Text("This Week", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            HintBox("Sessions completed each day this week. Today is highlighted in gold. Long-press to see this tip.") {
                WeeklyBars(s.weekly)
            }
        }

        // ── Streak tiles ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.coachMarkTarget(CoachMarkKeys.STATS_STREAK),
            horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.s),
        ) {
            HintBox("Consecutive days where you completed at least one session. Resets if you miss a day.", Modifier.weight(1f)) {
                StatTile("Current Streak", "${s.currentStreak} days")
            }
            HintBox("The longest run of back-to-back active days you've ever had.", Modifier.weight(1f)) {
                StatTile("Best Streak", "${s.longestStreak} days")
            }
        }

        // ── Goals ─────────────────────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(AnchorSpacing.s),
            modifier = Modifier.coachMarkTarget(CoachMarkKeys.STATS_GOALS),
        ) {
            Text("Goals", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

            if (g.exemptionCards > 0) {
                AnchorCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).background(AnchorColors.Gold, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Star, null, tint = AnchorColors.Bg, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(AnchorSpacing.m))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${g.exemptionCards} Exemption Card${if (g.exemptionCards != 1) "s" else ""}",
                                color = AnchorColors.OnBg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text(
                                "Earned by completing a goal. Use one to skip a session without penalty.",
                                color = AnchorColors.OnBgMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
            }

            if (g.activeGoal != null) {
                ActiveGoalCard(goal = g.activeGoal!!, currentSessions = g.currentSessions, daysRemaining = g.daysRemaining)
            } else {
                AnchorCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Flag, null, tint = AnchorColors.OnBgMuted)
                        Spacer(Modifier.width(AnchorSpacing.m))
                        Column(Modifier.weight(1f)) {
                            Text("No active goal", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Set a goal in Settings to track a multi-week commitment. Complete it on time to earn an Exemption Card.",
                                color = AnchorColors.OnBgMuted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }

            if (g.completedGoals.isNotEmpty()) {
                Text("Completed", color = AnchorColors.OnBgMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                g.completedGoals.forEach { goal -> CompletedGoalRow(goal) }
            }

            if (g.failedGoals.isNotEmpty()) {
                Text("Failed", color = AnchorColors.OnBgMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                g.failedGoals.forEach { goal -> FailedGoalRow(goal) }
            }
        }

        // ── About ─────────────────────────────────────────────────────────────
        AboutSection()

        Spacer(Modifier.height(AnchorSpacing.xl))
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun QuickStat(label: String, value: String, sublabel: String, modifier: Modifier = Modifier) {
    AnchorCard(modifier = modifier) {
        Text(label, color = AnchorColors.OnBgMuted, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AnchorColors.OnBg,
            maxLines = 1,
        )
        Text(sublabel, color = AnchorColors.OnBgMuted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    AnchorCard(modifier) {
        Text(label, color = AnchorColors.OnBgMuted, fontSize = 12.sp)
        Spacer(Modifier.height(AnchorSpacing.s))
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AnchorColors.OnBg)
    }
}

@Composable
private fun WeeklyBars(values: List<Int>) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val todayIdx = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.ordinal
    }

    AnchorCard {
        // Bars area
        Row(
            Modifier.fillMaxWidth().height(156.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { i, v ->
                val isToday = i == todayIdx
                // Cap bar at 85% of area height so count labels always fit above
                val h = ((v.toFloat() / max) * 0.85f).coerceIn(0.02f, 0.85f)
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // Count label above bar (only when non-zero)
                    Text(
                        if (v > 0) v.toString() else "",
                        color = if (isToday) AnchorColors.Gold else AnchorColors.OnBgMuted,
                        fontSize = 10.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Bar
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(h)
                            .background(
                                color = when {
                                    isToday && v > 0 -> AnchorColors.Gold
                                    v > 0 -> AnchorColors.Gold.copy(alpha = 0.42f)
                                    else -> AnchorColors.SurfaceAlt
                                },
                                shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp),
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Day labels row — separated so bars never overflow
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val isToday = i == todayIdx
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = if (isToday) AnchorColors.Gold else AnchorColors.OnBgMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveGoalCard(goal: Goal, currentSessions: Int, daysRemaining: Int) {
    val progress = (currentSessions.toFloat() / goal.targetSessions.toFloat()).coerceIn(0f, 1f)
    AnchorCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(AnchorColors.Gold, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Flag, null, tint = AnchorColors.Bg)
            }
            Spacer(Modifier.width(AnchorSpacing.m))
            Column(Modifier.weight(1f)) {
                Text(goal.title, color = AnchorColors.OnBg, fontWeight = FontWeight.Bold)
                Text(
                    "$daysRemaining day${if (daysRemaining != 1) "s" else ""} remaining",
                    color = if (daysRemaining <= 3) Color(0xFFFF6B6B) else AnchorColors.OnBgMuted,
                    fontSize = 12.sp,
                )
            }
            Text(
                "${(progress * 100).toInt()}%",
                color = AnchorColors.Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
        Spacer(Modifier.height(AnchorSpacing.m))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$currentSessions of ${goal.targetSessions} sessions",
                color = AnchorColors.OnBg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(AnchorSpacing.s))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = AnchorColors.Gold,
            trackColor = AnchorColors.Border,
        )
        Spacer(Modifier.height(AnchorSpacing.s))
        Text(
            "Complete before the deadline to earn an Exemption Card. Missing it resets your streak.",
            color = AnchorColors.OnBgMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun CompletedGoalRow(goal: Goal) {
    AnchorCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(AnchorColors.GoldMuted, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.EmojiEvents, null, tint = AnchorColors.Gold, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(AnchorSpacing.m))
            Column(Modifier.weight(1f)) {
                Text(goal.title, color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "${goal.targetSessions} sessions in ${goal.durationDays} days — completed",
                    color = AnchorColors.Gold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun FailedGoalRow(goal: Goal) {
    AnchorCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(AnchorColors.Border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Flag, null, tint = AnchorColors.OnBgMuted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(AnchorSpacing.m))
            Column(Modifier.weight(1f)) {
                Text(goal.title, color = AnchorColors.OnBgMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "${goal.sessionsCompleted}/${goal.targetSessions} sessions — streak was reset",
                    color = AnchorColors.OnBgMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(
            color = AnchorColors.Border,
            modifier = Modifier.padding(vertical = AnchorSpacing.s),
        )
        Spacer(Modifier.height(AnchorSpacing.s))
        Icon(
            Icons.Default.Timer,
            contentDescription = null,
            tint = AnchorColors.Border,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(AnchorSpacing.s))
        Text(
            "A work thought of by Joe,\nmade in relation with Jele Sphere.",
            color = AnchorColors.OnBgMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Anchor",
            color = AnchorColors.Border,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
    }
}
