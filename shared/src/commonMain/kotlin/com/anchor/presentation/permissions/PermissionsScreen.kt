package com.anchor.presentation.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.platform.isAndroid
import com.anchor.presentation.components.AnchorCard
import com.anchor.presentation.components.PrimaryButton
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorSpacing
import org.koin.compose.viewmodel.koinViewModel

private data class PermissionCopy(
    val title: String,
    val why: String,
    val activeUse: String,
    val icon: ImageVector,
    val granted: Boolean,
    val onGrant: () -> Unit,
)

@Composable
fun PermissionsScreen(onFinish: () -> Unit, vm: PermissionsViewModel = koinViewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    // Usage Access and Overlay are Android-only capabilities; hide them on iOS.
    val permissions = buildList {
        if (isAndroid()) {
            add(PermissionCopy(
                title = "Usage Access",
                why = "Lets Anchor detect drift from app usage and idle patterns.",
                activeUse = "Trigger timing can use real behavior signals.",
                icon = Icons.Default.RemoveRedEye,
                granted = state.usage,
                onGrant = vm::requestUsage,
            ))
            add(PermissionCopy(
                title = "Overlay Permission",
                why = "Lets Anchor show unavoidable decision screens over distracting apps.",
                activeUse = "Lock mode can interrupt avoidance in the moment.",
                icon = Icons.Default.Shield,
                granted = state.overlay,
                onGrant = vm::requestOverlay,
            ))
        }
        add(PermissionCopy(
            title = "Notifications",
            why = "Lets Anchor re-trigger delayed decisions and session prompts.",
            activeUse = "Delay loops and reminders can reach you outside the app.",
            icon = Icons.Default.Notifications,
            granted = state.notifications,
            onGrant = vm::requestNotifications,
        ))
        if (isAndroid()) {
            add(PermissionCopy(
                title = "Accessibility Service",
                why = "Required for Lock Mode to intercept blocked apps when you open them.",
                activeUse = "Lock Mode can redirect you away from distracting apps in real time.",
                icon = Icons.Default.Bolt,
                granted = state.accessibility,
                onGrant = vm::requestAccessibility,
            ))
        }
    }
    val grantedCount = permissions.count { it.granted }
    val canFinish = state.canFinish
    // Requirement hint text shown under the button when it's locked
    val requirementHint = if (isAndroid()) {
        when {
            !state.canFinish && state.grantedCount == 0 ->
                "Grant at least Usage Access and Overlay to continue — they are required for Anchor to work."
            !state.canFinish ->
                "Grant Usage Access and Overlay to continue. Notifications and Accessibility Service can be set up later."
            !state.accessibility ->
                "Accessibility Service not enabled — Lock Mode will not intercept apps until you enable it. You can do this now or later in Settings."
            else -> null
        }
    } else {
        if (!state.notifications) "Allow notifications so Anchor can reach you when it matters." else null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AnchorSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.l),
    ) {
        Text("Permissions", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = AnchorColors.OnBg)
        Text(
            "$grantedCount of ${permissions.size} granted. ${
                if (isAndroid()) "Usage Access and Overlay are essential — they can only be configured here during setup."
                else "Notifications let Anchor reach you at the right moment."
            }",
            color = AnchorColors.OnBgMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        permissions.forEach { permission ->
            PermissionCard(permission)
        }

        Button(
            onClick = vm::refresh,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AnchorColors.SurfaceAlt,
                contentColor = AnchorColors.OnBg,
            ),
        ) {
            Text("Check Permission Status", fontWeight = FontWeight.SemiBold)
        }

        // Requirement hint shown when the finish button is locked
        if (requirementHint != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AnchorColors.SurfaceAlt, RoundedCornerShape(12.dp))
                    .padding(AnchorSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.s),
            ) {
                Text("!", color = AnchorColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    requirementHint,
                    color = AnchorColors.OnBgMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        PrimaryButton(
            text = if (canFinish) "Finish Setup" else "Grant permissions to continue",
            enabled = canFinish,
            onClick = {
                vm.completeOnboarding()
                onFinish()
            },
        )
    }
}

@Composable
private fun PermissionCard(permission: PermissionCopy) {
    AnchorCard(border = !permission.granted) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).background(
                    if (permission.granted) AnchorColors.Gold else AnchorColors.SurfaceAlt,
                    RoundedCornerShape(14.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (permission.granted) Icons.Default.CheckCircle else permission.icon,
                    null,
                    tint = if (permission.granted) AnchorColors.Bg else AnchorColors.Gold,
                )
            }
            Spacer(Modifier.width(AnchorSpacing.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(permission.title, color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(AnchorSpacing.s))
                    StatusPill(permission.granted)
                }
                Spacer(Modifier.height(AnchorSpacing.s))
                Text(permission.why, color = AnchorColors.OnBgMuted, fontSize = 13.sp)
                Spacer(Modifier.height(AnchorSpacing.s))
                Text(
                    if (permission.granted) permission.activeUse else "Not active yet.",
                    color = if (permission.granted) AnchorColors.OnBg else AnchorColors.OnBgMuted,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(AnchorSpacing.m))
        Button(
            onClick = permission.onGrant,
            enabled = !permission.granted,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (permission.granted) AnchorColors.GoldMuted else AnchorColors.SurfaceAlt,
                contentColor = AnchorColors.OnBg,
                disabledContainerColor = AnchorColors.GoldMuted,
                disabledContentColor = Color(0xFFE8DEC4),
            ),
        ) {
            Text(if (permission.granted) "Active" else "Grant Permission", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusPill(granted: Boolean) {
    Box(
        Modifier.background(
            if (granted) AnchorColors.GoldMuted else AnchorColors.Border,
            RoundedCornerShape(999.dp),
        ).padding(horizontal = AnchorSpacing.s, vertical = AnchorSpacing.xs),
    ) {
        Text(
            if (granted) "Active" else "Needed",
            color = if (granted) Color(0xFFE8DEC4) else AnchorColors.OnBgMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
