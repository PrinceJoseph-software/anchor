package com.anchor.presentation.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.domain.model.Action
import com.anchor.domain.model.ActionIcon
import com.anchor.presentation.components.GoldButton
import com.anchor.presentation.components.PrimaryButton
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorSpacing
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingWelcomeScreen(onNext: () -> Unit) {
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }

    val phase1Alpha by animateFloatAsState(if (animateIn) 1f else 0f, tween(700))
    val phase1Offset by animateFloatAsState(if (animateIn) 0f else 32f, tween(700))
    val phase2Alpha by animateFloatAsState(if (animateIn) 1f else 0f, tween(700, delayMillis = 300))
    val phase2Offset by animateFloatAsState(if (animateIn) 0f else 24f, tween(700, delayMillis = 300))
    val phase3Alpha by animateFloatAsState(if (animateIn) 1f else 0f, tween(600, delayMillis = 600))
    val phase4Alpha by animateFloatAsState(if (animateIn) 1f else 0f, tween(600, delayMillis = 900))

    Column(
        Modifier
            .fillMaxSize()
            .background(AnchorColors.Bg)
            .padding(horizontal = AnchorSpacing.xl)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Spacer(Modifier.weight(0.12f))

        // ── Hero text block ──────────────────────────────────────────────────
        Column(
            Modifier
                .alpha(phase1Alpha)
                .offset(y = phase1Offset.dp),
        ) {
            Text(
                "Your focus,",
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                color = AnchorColors.OnBgMuted,
                lineHeight = 44.sp,
            )
            Text(
                "on purpose.",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AnchorColors.OnBg,
                lineHeight = 44.sp,
            )
            Spacer(Modifier.height(AnchorSpacing.m))
            // Gold accent underline
            Box(
                Modifier
                    .width(56.dp)
                    .height(3.dp)
                    .background(AnchorColors.Gold, RoundedCornerShape(2.dp))
            )
        }

        Spacer(Modifier.weight(0.08f))

        // ── Tagline ──────────────────────────────────────────────────────────
        Text(
            "Anchor detects when you drift from your goals and surfaces a decision — not a guilt trip.",
            color = AnchorColors.OnBgMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.alpha(phase2Alpha).offset(y = phase2Offset.dp),
        )

        Spacer(Modifier.weight(0.08f))

        // ── Three feature rows ───────────────────────────────────────────────
        Column(
            Modifier.alpha(phase3Alpha),
            verticalArrangement = Arrangement.spacedBy(AnchorSpacing.l),
        ) {
            WelcomeFeatureRow(
                number = "01",
                title = "Catch the drift",
                body = "Anchor watches your usage patterns and steps in at the right moment.",
            )
            WelcomeFeatureRow(
                number = "02",
                title = "One clear choice",
                body = "Start your session, delay it, or skip — no judgment, just clarity.",
            )
            WelcomeFeatureRow(
                number = "03",
                title = "Build the record",
                body = "Every decision is logged. Watch your follow-through rate climb over time.",
            )
        }

        Spacer(Modifier.weight(0.12f))

        // ── CTA ──────────────────────────────────────────────────────────────
        Column(
            Modifier.alpha(phase4Alpha).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AnchorSpacing.m),
        ) {
            GoldButton("Get Started", onNext)
            Text(
                "Takes about 2 minutes to set up.",
                color = AnchorColors.OnBgMuted,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        Spacer(Modifier.height(AnchorSpacing.l))
    }
}

@Composable
private fun WelcomeFeatureRow(number: String, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        // Gold number badge
        Box(
            Modifier
                .size(36.dp)
                .background(AnchorColors.SurfaceAlt, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AnchorColors.Gold,
            )
        }
        Spacer(Modifier.width(AnchorSpacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(body, color = AnchorColors.OnBgMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun OnboardingActionsScreen(
    onContinue: () -> Unit,
    vm: OnboardingViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    var showCustom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AnchorSpacing.xl)) {
        Text("What do you want\nto focus on?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AnchorColors.OnBg)
        Spacer(Modifier.height(AnchorSpacing.s))
        Text("Select at least one activity you'd like to commit to. Anchor will help keep you accountable.", color = AnchorColors.OnBgMuted, fontSize = 14.sp)
        Spacer(Modifier.height(AnchorSpacing.xl))

        state.available.forEach { a ->
            ActionRow(a, selected = a.id in state.selected, onClick = { vm.toggle(a.id) })
            Spacer(Modifier.height(AnchorSpacing.m))
        }

        if (showCustom) {
            OutlinedTextField(
                value = customText, onValueChange = { customText = it },
                placeholder = { Text("Custom action") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AnchorSpacing.s))
            GoldButton(
                text = "Add",
                onClick = {
                    vm.addCustom(customText)
                    customText = ""
                    showCustom = false
                }
            )
        } else {
            DashedAddRow("Add custom action") { showCustom = true }
        }

        Spacer(Modifier.height(AnchorSpacing.xl))
        PrimaryButton("Continue", { vm.commit(onContinue) }, enabled = state.canContinue)
    }
}

@Composable
fun OnboardingPreviewScreen(
    onTryIntervention: () -> Unit,
    onContinue: () -> Unit,
) {
    var animateIn by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        animateIn = true
    }
    
    val contentAlpha = animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(600)
    ).value
    
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AnchorSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(AnchorSpacing.xl))
        Box(
            Modifier
                .size(80.dp)
                .background(AnchorColors.Gold, RoundedCornerShape(20.dp))
                .alpha(contentAlpha),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Timer, null, tint = AnchorColors.Bg, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(AnchorSpacing.l))
        Text(
            "How Anchor Works",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AnchorColors.OnBg,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.alpha(contentAlpha)
        )
        Spacer(Modifier.height(AnchorSpacing.m))
        Text(
            "When you open a distraction, Anchor interrupts with a decision point. You choose: start your focused action, delay and try again later, or skip with a reason.",
            color = AnchorColors.OnBgMuted,
            fontSize = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.alpha(contentAlpha),
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(AnchorSpacing.xl))
        
        Column(
            Modifier
                .alpha(contentAlpha)
                .fillMaxWidth()
                .background(AnchorColors.Surface, RoundedCornerShape(16.dp))
                .padding(AnchorSpacing.l),
            verticalArrangement = Arrangement.spacedBy(AnchorSpacing.m)
        ) {
            listOf(
                Pair("✓ Start", "One tap to begin your session"),
                Pair("⏱ Delay", "Hold for 15s to snooze the interruption"),
                Pair("✗ Skip", "Explain why you're not starting now"),
            ).forEach { (label, desc) ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(label, fontWeight = FontWeight.SemiBold, color = AnchorColors.Gold, modifier = Modifier.width(80.dp))
                    Text(desc, color = AnchorColors.OnBgMuted, fontSize = 14.sp)
                }
            }
        }
        
        Spacer(Modifier.height(AnchorSpacing.xl))
        GoldButton("Try Decision Point", onTryIntervention, modifier = Modifier.alpha(contentAlpha))
        Spacer(Modifier.height(AnchorSpacing.m))
        PrimaryButton("Continue to Permissions", onContinue, modifier = Modifier.alpha(contentAlpha))
    }
}

@Composable
private fun ActionRow(action: Action, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) AnchorColors.Gold else AnchorColors.Border
    val backgroundColor = if (selected) AnchorColors.GoldMuted else AnchorColors.Surface
    
    val animatedBorder = animateColorAsState(border).value
    val animatedBg = animateColorAsState(backgroundColor).value
    
    Row(
        Modifier.fillMaxWidth().height(64.dp)
            .background(animatedBg, RoundedCornerShape(16.dp))
            .border(1.dp, animatedBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = AnchorSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon.vector(), null, tint = AnchorColors.OnBgMuted)
        Spacer(Modifier.width(AnchorSpacing.m))
        Text(action.name, color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DashedAddRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp)
            .border(1.dp, AnchorColors.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = AnchorSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = AnchorColors.OnBgMuted)
        Spacer(Modifier.width(AnchorSpacing.m))
        Text(label, color = AnchorColors.OnBgMuted)
    }
}

private fun ActionIcon.vector(): ImageVector = when (this) {
    ActionIcon.Workout -> Icons.Default.FitnessCenter
    ActionIcon.Study -> Icons.Default.School
    ActionIcon.Reading -> Icons.Default.MenuBook
    ActionIcon.Generic -> Icons.Default.Timer
}
