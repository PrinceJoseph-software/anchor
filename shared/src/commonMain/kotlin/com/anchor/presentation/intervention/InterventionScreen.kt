package com.anchor.presentation.intervention

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.presentation.components.AnchorCard
import com.anchor.presentation.components.GoldButton
import com.anchor.presentation.components.PrimaryButton
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorSpacing
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

private const val DELAY_HOLD_MS = 15_000L

@Composable
fun InterventionScreen(
    triggerId: String,
    onClose: () -> Unit,
    onSessionStarted: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    vm: InterventionViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val actions by vm.actionsFlow.collectAsState()
    LaunchedEffect(triggerId) { vm.bind(triggerId) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AnchorColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(AnchorSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(AnchorSpacing.xl))
        Text("Decision point", color = AnchorColors.OnBgMuted)
        Spacer(Modifier.height(AnchorSpacing.s))
        Text(
            "You're likely free right now.",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AnchorColors.OnBg,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AnchorSpacing.l))
        Text(
            "One decision. Start, delay, or skip with a reason.",
            color = AnchorColors.OnBgMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AnchorSpacing.xl))

        actions.forEach { action ->
            GoldButton(
                text = "Start ${action.name}",
                onClick = { vm.start(action.id, onSessionStarted) },
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        if (actions.isEmpty()) {
            AnchorCard {
                Text(
                    "No focus actions configured.",
                    color = AnchorColors.OnBg,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AnchorSpacing.s))
                Text(
                    "Add activities in Settings so Anchor can help you refocus.",
                    color = AnchorColors.OnBgMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(AnchorSpacing.m))
                PrimaryButton("Go to Settings", onNavigateToSettings)
            }
        }

        Spacer(Modifier.height(AnchorSpacing.xl))
        if (!state.delaying && !state.skipping) {
            TextButton(onClick = vm::openDelay) {
                Text("Delay once", color = AnchorColors.OnBgMuted)
            }
            TextButton(onClick = vm::openSkip) {
                Text("Skip with reason", color = AnchorColors.OnBgMuted)
            }
        }
        if (state.delaying) {
            DelayHoldPanel(onConfirm = { vm.confirmDelay(onClose) })
        }
        val minLen by vm.minReasonLength.collectAsState()
        if (state.skipping) {
            SkipPanel(
                reason = state.skipReason,
                minReasonLength = minLen,
                onReason = vm::setSkipReason,
                onConfirm = { vm.confirmSkip(onClose) },
            )
        }
    }
}

@Composable
private fun DelayHoldPanel(onConfirm: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }

    LaunchedEffect(holding) {
        if (holding) {
            val start = nowMs()
            while (holding && elapsed < DELAY_HOLD_MS) {
                delay(50)
                elapsed = nowMs() - start
            }
            if (elapsed >= DELAY_HOLD_MS) onConfirm()
        } else {
            elapsed = 0L
        }
    }

    val pct = (elapsed.toFloat() / DELAY_HOLD_MS).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth()) {
        Text("Hold for 15 seconds — delay is never passive.", color = AnchorColors.OnBgMuted)
        Spacer(Modifier.height(AnchorSpacing.m))
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    if (holding) AnchorColors.GoldMuted else AnchorColors.SurfaceAlt,
                    RoundedCornerShape(28.dp),
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (holding && pct > 0f) {
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    color = AnchorColors.Gold,
                    trackColor = AnchorColors.GoldMuted,
                )
            }
            Text(
                if (holding) "${(pct * 100).toInt()}% — keep holding" else "Hold to delay",
                color = if (holding) AnchorColors.OnBg else AnchorColors.OnBgMuted,
                fontWeight = if (holding) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun SkipPanel(
    reason: String,
    minReasonLength: Int,
    onReason: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = reason,
            onValueChange = onReason,
            placeholder = { Text("Why are you skipping?") },
            modifier = Modifier.fillMaxWidth(),
            isError = reason.length < minReasonLength && reason.isNotBlank(),
            supportingText = {
                if (reason.length < minReasonLength) {
                    Text("Reason must be at least $minReasonLength characters (${reason.length}/$minReasonLength)")
                }
            },
        )
        Spacer(Modifier.height(AnchorSpacing.m))
        Button(
            onClick = onConfirm,
            enabled = reason.length >= minReasonLength,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AnchorColors.Gold,
                contentColor = AnchorColors.Bg,
                disabledContainerColor = AnchorColors.SurfaceAlt,
                disabledContentColor = AnchorColors.OnBgMuted,
            ),
        ) { Text("Log Skip") }
    }
}

private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
