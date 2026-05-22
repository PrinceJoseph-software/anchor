package com.anchor.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.domain.model.Session
import com.anchor.domain.model.SessionOutcome
import com.anchor.presentation.coachmark.CoachMarkKeys
import com.anchor.presentation.coachmark.coachMarkTarget
import com.anchor.presentation.components.AnchorCard
import com.anchor.presentation.components.GoldButton
import com.anchor.presentation.components.HintBox
import com.anchor.presentation.components.PrimaryButton
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorSpacing
import kotlinx.datetime.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onOpenIntervention: (String) -> Unit,
    onSessionStarted: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    vm: HomeViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AnchorSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.l),
    ) {
        Spacer(Modifier.height(AnchorSpacing.xl))

        Column {
            Text("Anchor", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = AnchorColors.OnBg)
            val dayName = state.today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercaseChar() }
            val monthName = state.today.month.name.lowercase().replaceFirstChar { it.uppercaseChar() }
            Text(
                "$dayName, $monthName ${state.today.dayOfMonth}",
                color = AnchorColors.OnBgMuted,
            )
        }

        HintBox("Tracks how many sessions you've completed today vs. your daily target. Adjust your target in Settings → Session Settings.") {
            AnchorCard(modifier = Modifier.coachMarkTarget(CoachMarkKeys.HOME_PROGRESS)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's Progress", color = AnchorColors.OnBgMuted)
                    Text("${state.progress.completed} of ${state.progress.target}",
                        color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(AnchorSpacing.m))
                LinearProgressIndicator(
                    progress = { (state.progress.completed.toFloat() / state.progress.target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = AnchorColors.Gold, trackColor = AnchorColors.Border,
                )
                Spacer(Modifier.height(AnchorSpacing.s))
                Text(
                    if (state.progress.completed >= state.progress.target) "Target reached!"
                    else if (state.progress.completed == 0) "Start your first session today"
                    else "${state.progress.target - state.progress.completed} more to hit target",
                    color = AnchorColors.OnBgMuted, fontSize = 13.sp,
                )
            }
        }

        Column {
            Text(
                "Start Now",
                color = AnchorColors.OnBg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.coachMarkTarget(CoachMarkKeys.HOME_START_NOW),
            )
            Spacer(Modifier.height(AnchorSpacing.m))
            if (state.actions.isEmpty()) {
                AnchorCard {
                    Text("No activities yet", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(AnchorSpacing.s))
                    Text("Add your focus activities in Settings to get started.", color = AnchorColors.OnBgMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(AnchorSpacing.m))
                    PrimaryButton("Go to Settings", onNavigateToSettings)
                }
            } else {
                state.actions.forEach { action ->
                    GoldButton(
                        text = "Start ${action.name}",
                        onClick = { vm.startNow(action.id, onSessionStarted) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }

        Column {
            Text("Next Trigger", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.height(AnchorSpacing.m))
            val t = state.nextTrigger
            HintBox("Anchor watches your usage patterns to find natural gaps in your day. When it detects a good moment, it nudges you to start a session. Tap the card to open the intervention now.") {
                AnchorCard(modifier = Modifier
                    .coachMarkTarget(CoachMarkKeys.HOME_TRIGGER)
                    .clickable(enabled = t != null) {
                    t?.let { onOpenIntervention(it.id) }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                t?.let { "You're likely free right now" } ?: "No trigger predicted",
                                color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                t?.reason ?: "Building your activity profile...",
                                color = AnchorColors.OnBgMuted, fontSize = 13.sp,
                            )
                        }
                        if (t != null) Icon(Icons.Default.ArrowForward, null, tint = AnchorColors.Gold)
                    }
                }
            }
        }

        Column {
            Text("Today's Sessions", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.height(AnchorSpacing.m))
            if (state.todaysSessions.isEmpty()) {
                AnchorCard {
                    Text("No sessions today", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(AnchorSpacing.s))
                    Text("Start your first session above.", color = AnchorColors.OnBgMuted, fontSize = 13.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.s)) {
                    state.todaysSessions.forEach { s ->
                        SessionRow(s, actionName = state.actions.firstOrNull { it.id == s.actionId }?.name ?: "Action") {
                            vm.complete(s.id)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(AnchorSpacing.s))
    }
}

@Composable
private fun SessionRow(s: Session, actionName: String, onComplete: () -> Unit) {
    val tz = TimeZone.currentSystemDefault()
    val time = s.startedAt.toLocalDateTime(tz).time
    Row(
        Modifier.fillMaxWidth().background(AnchorColors.Surface, RoundedCornerShape(16.dp))
            .padding(AnchorSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(actionName, color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
            Text("${time.hour.toString().padStart(2,'0')}:${time.minute.toString().padStart(2,'0')}",
                color = AnchorColors.OnBgMuted, fontSize = 13.sp)
        }
        val completed = s.outcome == SessionOutcome.Completed
        val inProgress = s.outcome == SessionOutcome.InProgress
        val circleColor = when {
            completed -> AnchorColors.Gold
            inProgress -> AnchorColors.Danger
            else -> AnchorColors.SurfaceAlt   // Abandoned
        }
        val hint = when {
            completed -> "Session completed."
            inProgress -> "Session in progress. Tap to mark as completed."
            else -> "Session abandoned."
        }
        HintBox(hint) {
            Box(
                Modifier.size(22.dp).background(circleColor, CircleShape)
                    .clickable(enabled = inProgress, onClick = onComplete)
            )
        }
    }
}
