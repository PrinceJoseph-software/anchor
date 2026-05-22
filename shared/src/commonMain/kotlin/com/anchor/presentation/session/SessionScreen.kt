package com.anchor.presentation.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.domain.model.SessionOutcome
import com.anchor.platform.startSessionNotification
import com.anchor.platform.stopSessionNotification
import com.anchor.presentation.components.GoldButton
import com.anchor.presentation.components.PrimaryButton
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorSpacing
import androidx.compose.material.icons.filled.Timer
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SessionScreen(
    sessionId: String,
    onDone: () -> Unit,
    vm: SessionViewModel = koinViewModel(key = sessionId, parameters = { parametersOf(sessionId) }),
) {
    val state by vm.state.collectAsState()

    // Start a persistent notification as soon as the session is confirmed InProgress,
    // so the user stays aware the session is running if they switch to another app.
    // Stop it whenever this screen leaves composition (complete, abandon, or nav-back).
    LaunchedEffect(state.session) {
        val session = state.session ?: return@LaunchedEffect
        if (session.outcome == SessionOutcome.InProgress) {
            startSessionNotification(state.actionName, session.startedAt.toEpochMilliseconds())
        }
    }
    DisposableEffect(Unit) {
        onDispose { stopSessionNotification() }
    }

    // Completed → reward screen
    if (state.isCompleted) {
        RewardScreen(state.completedDurationSeconds, onDone)
        return
    }

    // Abandoned → penalty screen (stays until user taps Return Home)
    if (state.isAbandoned) {
        AbandonedScreen(
            durationSeconds = state.abandonedDurationSeconds,
            onDone = onDone,
        )
        return
    }

    // Intercept the hardware back button — route through the confirmation dialog
    // rather than silently leaving the session in InProgress state.
    BackHandler(enabled = true) {
        vm.requestAbandon()
    }

    // Abandon confirmation dialog
    if (state.showAbandonConfirm) {
        AlertDialog(
            onDismissRequest = vm::dismissAbandon,
            containerColor = AnchorColors.Surface,
            title = { Text("Abandon session?", color = AnchorColors.OnBg, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.s)) {
                    Text(
                        "Leaving now marks this session as abandoned.",
                        color = AnchorColors.OnBgMuted,
                    )
                    Text(
                        "It will be recorded in your history and will not count toward today's progress or your current streak.",
                        color = AnchorColors.OnBgMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            },
            confirmButton = {
                // Note: no onDone() here — AbandonedScreen handles navigation
                TextButton(onClick = { vm.confirmAbandon() }) {
                    Text("Abandon", color = AnchorColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissAbandon) {
                    Text("Keep going", color = AnchorColors.Gold)
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AnchorColors.Bg)
            .padding(AnchorSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text("Active session", color = AnchorColors.OnBgMuted)
        Spacer(Modifier.height(AnchorSpacing.s))
        Text(
            state.actionName,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = AnchorColors.OnBg,
        )
        Spacer(Modifier.height(AnchorSpacing.xl))

        Text(
            "${state.displayMinutes.toString().padStart(2, '0')}:${state.displaySeconds.toString().padStart(2, '0')}",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = AnchorColors.Gold,
        )

        Spacer(Modifier.height(AnchorSpacing.m))

        if (!state.canComplete) {
            val rem = state.remainingMinutes
            Text(
                "Keep going! $rem min${if (rem != 1L) "s" else ""} remaining",
                color = AnchorColors.Gold,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "Minimum met. You can complete when ready.",
                color = AnchorColors.OnBgMuted,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        GoldButton(
            text = "Complete Session",
            onClick = vm::complete,
            enabled = state.canComplete,
        )
        Spacer(Modifier.height(AnchorSpacing.m))
        TextButton(
            onClick = vm::requestAbandon,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Abandon session", color = AnchorColors.OnBgMuted, fontSize = 14.sp)
        }
    }
}

// ── Abandoned result screen ────────────────────────────────────────────────────

@Composable
private fun AbandonedScreen(durationSeconds: Long, onDone: () -> Unit) {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val durationLabel = when {
        minutes > 0 -> "$minutes min ${seconds}s"
        else -> "${seconds}s"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AnchorColors.Bg)
            .padding(AnchorSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .size(80.dp)
                .background(Color(0xFF3A1A1A), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Abandoned",
                tint = AnchorColors.Danger,
                modifier = Modifier.size(38.dp),
            )
        }

        Spacer(Modifier.height(AnchorSpacing.l))

        Text(
            "Session abandoned.",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AnchorColors.OnBg,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(AnchorSpacing.s))

        if (durationSeconds > 0) {
            Text(
                "You lasted $durationLabel.",
                color = AnchorColors.OnBgMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AnchorSpacing.s))
        }

        Surface(
            color = Color(0xFF2A1010),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(AnchorSpacing.l),
                verticalArrangement = Arrangement.spacedBy(AnchorSpacing.s),
            ) {
                Text(
                    "Penalty recorded",
                    color = AnchorColors.Danger,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    "This session will appear in your history as abandoned and will not count toward today's progress or your streak.",
                    color = AnchorColors.OnBgMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(AnchorSpacing.l))

        Text(
            "Every completed session builds momentum. Come back when you're ready.",
            color = AnchorColors.OnBgMuted,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton("Return Home", onDone)
    }
}

// ── Completed reward screen ────────────────────────────────────────────────────

@Composable
private fun RewardScreen(durationSeconds: Long, onDone: () -> Unit) {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val durationLabel: String? = when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        durationSeconds > 0 -> "< 1 min"
        else -> null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AnchorColors.Bg)
            .padding(AnchorSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(80.dp)
                .background(AnchorColors.Gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Completed",
                tint = AnchorColors.Bg,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(AnchorSpacing.l))
        Text(
            "You showed up.",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AnchorColors.OnBg,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AnchorSpacing.s))
        if (durationLabel != null) {
            Surface(
                color = AnchorColors.GoldMuted,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = AnchorSpacing.l, vertical = AnchorSpacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = AnchorColors.Gold,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(AnchorSpacing.s))
                    Text(
                        "Focused for $durationLabel",
                        color = AnchorColors.Gold,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(Modifier.height(AnchorSpacing.m))
        }
        Text(
            "Progress updated. Momentum reinforced.",
            color = AnchorColors.OnBgMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Return Home", onDone)
    }
}
