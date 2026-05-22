package com.anchor.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Apps
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import com.anchor.platform.isAndroid
import com.anchor.platform.shareText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.domain.model.Sensitivity
import com.anchor.presentation.coachmark.CoachMarkKeys
import com.anchor.presentation.coachmark.coachMarkTarget
import com.anchor.presentation.components.AnchorCard
import com.anchor.presentation.components.HintBox
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorSpacing
import org.koin.compose.viewmodel.koinViewModel

private fun blockAfterLabel(minutes: Int): String = when {
    minutes <= 0 -> "Always"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "${minutes / 60} hr ${minutes % 60} min"
}

// Human-readable labels for the Sensitivity enum so chips don't just say "Low/Medium/High"
private fun Sensitivity.displayLabel() = when (this) {
    Sensitivity.Low -> "Rarely"
    Sensitivity.Medium -> "Sometimes"
    Sensitivity.High -> "Often"
}

private fun Sensitivity.chipHint() = when (this) {
    Sensitivity.Low -> "Fires only when Anchor is very confident you're free. Fewer prompts."
    Sensitivity.Medium -> "Balanced default. Prompts when the signal is reasonably clear."
    Sensitivity.High -> "Fires at the first sign of free time. More prompts, weaker signals."
}

@Composable
fun SettingsScreen(
    onNavigateToBlockedApps: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    vm: SettingsViewModel = koinViewModel(),
    goalVm: com.anchor.presentation.stats.GoalViewModel = org.koin.compose.viewmodel.koinViewModel(),
) {
    val s by vm.state.collectAsState()
    val g by goalVm.state.collectAsState()
    var newName by remember { mutableStateOf("") }
    var showSensitivityInfo by remember { mutableStateOf(false) }
    var showLevelInfo by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var showBlockedAppsDialog by remember { mutableStateOf(false) }

    // Export dialog
    if (showExportDialog && s.exportJson != null) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        val exportJson = s.exportJson!!
        val exportSummary = s.exportSummary
        AlertDialog(
            onDismissRequest = { showExportDialog = false; vm.clearExport() },
            containerColor = AnchorColors.Surface,
            title = { Text("Export Data", color = AnchorColors.OnBg, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.m)) {
                    Text(
                        "Your complete Anchor backup is ready. Copy or share it to transfer to another device or keep as a backup.",
                        color = AnchorColors.OnBgMuted, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                    // Summary chip row
                    if (exportSummary != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(AnchorColors.GoldMuted, RoundedCornerShape(10.dp))
                                .padding(horizontal = AnchorSpacing.m, vertical = AnchorSpacing.s),
                        ) {
                            Text(
                                exportSummary.label(),
                                color = AnchorColors.Gold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    // JSON preview
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(AnchorColors.SurfaceAlt, RoundedCornerShape(10.dp))
                            .padding(AnchorSpacing.s)
                            .heightIn(max = 80.dp)
                    ) {
                        Text(
                            exportJson.take(160) + if (exportJson.length > 160) "…" else "",
                            color = AnchorColors.OnBgMuted,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                    // Action row: Copy + Share
                    Row(horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.s)) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(exportJson))
                                showExportDialog = false; vm.clearExport()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy", fontSize = 13.sp)
                        }
                        if (isAndroid()) {
                            OutlinedButton(
                                onClick = {
                                    shareText(exportJson)
                                    showExportDialog = false; vm.clearExport()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share", fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExportDialog = false; vm.clearExport() }) { Text("Close") } },
        )
    }

    // Import dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false; importText = ""; vm.clearImportState() },
            containerColor = AnchorColors.Surface,
            title = { Text("Import Data", color = AnchorColors.OnBg, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.m)) {
                    when {
                        s.importSuccess -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = AnchorColors.Gold,
                                modifier = Modifier.size(32.dp).align(Alignment.CenterHorizontally),
                            )
                            Text(
                                "Import successful!",
                                color = AnchorColors.Gold,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Your actions, goals, and settings have been restored.",
                                color = AnchorColors.OnBgMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        else -> {
                            Text(
                                "Paste your exported JSON below. Actions will be replaced; goals will be merged (existing goals kept); settings restored.",
                                color = AnchorColors.OnBgMuted, fontSize = 13.sp, lineHeight = 18.sp,
                            )
                            if (s.importError != null) {
                                Surface(
                                    color = Color(0xFF2A1010),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        s.importError!!,
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(AnchorSpacing.s),
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = importText,
                                onValueChange = { importText = it },
                                placeholder = { Text("Paste exported JSON here…", color = AnchorColors.OnBgMuted, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AnchorColors.Gold,
                                    unfocusedBorderColor = AnchorColors.Border,
                                    focusedTextColor = AnchorColors.OnBg,
                                    unfocusedTextColor = AnchorColors.OnBg,
                                    focusedContainerColor = AnchorColors.SurfaceAlt,
                                    unfocusedContainerColor = AnchorColors.SurfaceAlt,
                                ),
                            )
                            if (importText.isNotBlank()) {
                                Text(
                                    "${importText.length} characters pasted",
                                    color = AnchorColors.OnBgMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!s.importSuccess) {
                    TextButton(
                        onClick = { vm.importData(importText) },
                        enabled = importText.isNotBlank(),
                    ) { Text("Import", color = AnchorColors.Gold) }
                } else {
                    TextButton(onClick = { showImportDialog = false; importText = ""; vm.clearImportState() }) {
                        Text("Done", color = AnchorColors.Gold)
                    }
                }
            },
            dismissButton = {
                if (!s.importSuccess) {
                    TextButton(onClick = { showImportDialog = false; importText = ""; vm.clearImportState() }) { Text("Cancel") }
                }
            },
        )
    }

    // Goal creation dialog
    if (showGoalDialog) {
        GoalCreationDialog(
            hasActiveGoal = g.activeGoal != null,
            onDismiss = { showGoalDialog = false },
            onCreate = { title, sessions, days ->
                goalVm.createGoal(title, sessions, days)
                showGoalDialog = false
            },
        )
    }

    if (showSensitivityInfo) {
        SensitivityInfoDialog { showSensitivityInfo = false }
    }
    if (showLevelInfo) {
        LevelInfoDialog { showLevelInfo = false }
    }
    if (showBlockedAppsDialog) {
        BlockedAppsListDialog(
            onDismiss = { showBlockedAppsDialog = false },
            onManage = { showBlockedAppsDialog = false; onNavigateToBlockedApps() },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AnchorSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.l),
    ) {
        Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = AnchorColors.OnBg)

        // ── Actions ──────────────────────────────────────────────────────────
        Text("Your Actions", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(
            "The focus activities Anchor suggests when it detects you drifting. Add the things you actually want to do.",
            color = AnchorColors.OnBgMuted, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(AnchorSpacing.s))
        s.actions.forEach { a ->
            Row(
                Modifier.fillMaxWidth().background(AnchorColors.Surface, RoundedCornerShape(16.dp))
                    .padding(AnchorSpacing.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a.name, color = AnchorColors.OnBg, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.remove(a.id) }) {
                    Icon(Icons.Default.Delete, null, tint = AnchorColors.OnBgMuted)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { if (it.length <= 32) newName = it },
                placeholder = { Text("New action") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(AnchorSpacing.s))
            Button(
                onClick = { vm.addAction(newName); newName = "" },
                enabled = newName.isNotBlank(),
            ) { Text("Add") }
        }

        // ── Intervention Settings header ──────────────────────────────────
        Text("Intervention Settings", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        AnchorCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).background(AnchorColors.Gold, RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(AnchorSpacing.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Sensitivity controls when Anchor fires.",
                        color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                    Text(
                        "Intervention Level controls what happens when it does.",
                        color = AnchorColors.OnBgMuted, fontSize = 13.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(AnchorSpacing.xs))

        // ── Trigger Sensitivity ───────────────────────────────────────────
        AnchorCard(modifier = Modifier.coachMarkTarget(CoachMarkKeys.SETTINGS_TRIGGERS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = AnchorColors.Gold)
                Spacer(Modifier.width(AnchorSpacing.s))
                Column(Modifier.weight(1f)) {
                    Text("Trigger Sensitivity", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                    Text(
                        "How often should Anchor prompt you?",
                        color = AnchorColors.OnBgMuted, fontSize = 12.sp,
                    )
                }
                IconButton(onClick = { showSensitivityInfo = true }) {
                    Icon(Icons.Default.Info, null, tint = AnchorColors.OnBgMuted, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(AnchorSpacing.m))
            // HintBox cannot be used here with weight(1f) — TooltipBox wraps content and
            // prevents the Row from distributing space evenly, hiding all chips after the first.
            // The ⓘ button above already provides per-level context.
            Row(horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.s)) {
                Sensitivity.entries.forEach { sens ->
                    val selected = sens == s.sensitivity
                    Box(
                        Modifier.weight(1f).height(44.dp)
                            .background(
                                if (selected) AnchorColors.Gold else AnchorColors.SurfaceAlt,
                                RoundedCornerShape(22.dp),
                            )
                            .clickable { vm.setSensitivity(sens) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            sens.displayLabel(),
                            color = if (selected) AnchorColors.Bg else AnchorColors.OnBg,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        // ── Intervention Level ────────────────────────────────────────────
        AnchorCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = AnchorColors.Gold)
                Spacer(Modifier.width(AnchorSpacing.s))
                Column(Modifier.weight(1f)) {
                    Text("Intervention Level", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                    Text(
                        "What happens when Anchor fires?",
                        color = AnchorColors.OnBgMuted, fontSize = 12.sp,
                    )
                }
                IconButton(onClick = { showLevelInfo = true }) {
                    Icon(Icons.Default.Info, null, tint = AnchorColors.OnBgMuted, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(AnchorSpacing.m))
            Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.s)) {
                com.anchor.domain.model.InterventionLevel.entries.forEach { level ->
                    val selected = level == s.interventionLevel
                    val (levelIcon, title, desc) = when (level) {
                        com.anchor.domain.model.InterventionLevel.Low ->
                            Triple(Icons.Default.Notifications, "Gentle", "Notification only — easy to dismiss, you stay in control")
                        com.anchor.domain.model.InterventionLevel.Medium ->
                            Triple(Icons.Default.Bolt, "Firm", "Decision screen appears — you must choose before continuing")
                        com.anchor.domain.model.InterventionLevel.High ->
                            Triple(Icons.Default.Lock, "Strict", "App is blocked — no way past without making a decision")
                    }
                    HintBox(hint = desc) {
                        Row(
                            Modifier.fillMaxWidth().height(64.dp)
                                .background(
                                    if (selected) AnchorColors.GoldMuted else AnchorColors.SurfaceAlt,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { vm.setInterventionLevel(level) }
                                .padding(AnchorSpacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.m),
                        ) {
                            Icon(levelIcon, null, tint = if (selected) AnchorColors.Gold else AnchorColors.OnBgMuted, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(desc, color = AnchorColors.OnBgMuted, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                            if (selected) Icon(Icons.Default.Check, null, tint = AnchorColors.Gold)
                        }
                    }
                }
            }
        }

        ToggleCard(Icons.Default.Lock, "Lock Mode", "Intercept distracting apps when opened", s.lockMode, vm::setLock)
        if (s.lockMode) {
            // Accessibility Service warning — Lock Mode won't work without it
            val controller = remember { com.anchor.platform.PermissionController() }
            val accessibilityEnabled = remember { mutableStateOf(controller.hasAccessibilityService()) }
            if (!accessibilityEnabled.value) {
                AnchorCard(modifier = Modifier.clickable {
                    controller.requestAccessibilityService()
                    accessibilityEnabled.value = controller.hasAccessibilityService()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(Color(0xFF3A1A1A), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Notifications, null, tint = AnchorColors.Danger, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(AnchorSpacing.m))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Accessibility Service not enabled",
                                color = AnchorColors.Danger,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            Text(
                                "Lock Mode cannot intercept apps until the Anchor Accessibility Service is active. Tap to open Accessibility Settings.",
                                color = AnchorColors.OnBgMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
            }

            AnchorCard(modifier = Modifier.clickable(onClick = onNavigateToBlockedApps)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = AnchorColors.Gold)
                    Spacer(Modifier.width(AnchorSpacing.m))
                    Column(Modifier.weight(1f)) {
                        Text("Blocked Apps", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (s.blockedAppsCount == 0) "No apps blocked yet — tap to add"
                            else "${s.blockedAppsCount} app${if (s.blockedAppsCount != 1) "s" else ""} blocked — tap to manage",
                            color = AnchorColors.OnBgMuted,
                            fontSize = 12.sp,
                        )
                    }
                    if (s.blockedAppsCount > 0) {
                        TextButton(
                            onClick = { showBlockedAppsDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text("View", color = AnchorColors.Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    } else {
                        Icon(Icons.Default.Add, null, tint = AnchorColors.Gold, modifier = Modifier.size(18.dp))
                    }
                }
            }
            TimeLimitCard(
                blockAfterMinutes = s.blockAfterMinutes,
                onSet = { vm.setBlockAfterMinutes(it) },
            )
        }
        ToggleCard(Icons.Default.Notifications, "Notifications", "Get reminded to check in and receive intervention alerts", s.notifications, vm::setNotif)

        // ── Session Settings ──────────────────────────────────────────────
        Text("Session Settings", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

        HintBox(hint = "The shortest time a session can last before you're allowed to mark it complete. Prevents quick tap-outs.") {
            AnchorCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = AnchorColors.Gold)
                    Spacer(Modifier.width(AnchorSpacing.m))
                    Column(Modifier.weight(1f)) {
                        Text("Minimum Session Duration", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (s.minSessionDuration == 0) "Sessions can be completed immediately"
                            else "Sessions must last at least ${s.minSessionDuration} minute${if (s.minSessionDuration != 1) "s" else ""}",
                            color = AnchorColors.OnBgMuted, fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(AnchorSpacing.m))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.m)) {
                    IconButton(onClick = { if (s.minSessionDuration > 0) vm.setMinSessionDuration(s.minSessionDuration - 1) }) {
                        Icon(Icons.Default.Remove, null, tint = AnchorColors.Gold, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        if (s.minSessionDuration == 0) "No minimum" else "${s.minSessionDuration} min",
                        color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { if (s.minSessionDuration < 60) vm.setMinSessionDuration(s.minSessionDuration + 1) }) {
                        Icon(Icons.Default.Add, null, tint = AnchorColors.Gold, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        HintBox(hint = "How many completed sessions you're aiming for each day. The home screen tracks your progress toward this.") {
            AnchorCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = AnchorColors.Gold)
                    Spacer(Modifier.width(AnchorSpacing.m))
                    Column(Modifier.weight(1f)) {
                        Text("Daily Target", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                        Text("Sessions you aim to complete each day", color = AnchorColors.OnBgMuted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(AnchorSpacing.m))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.m),
                ) {
                    IconButton(onClick = { if (s.dailyTarget > 1) vm.setDailyTarget(s.dailyTarget - 1) }) {
                        Icon(Icons.Default.Remove, null, tint = AnchorColors.Gold, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        "${s.dailyTarget} session${if (s.dailyTarget != 1) "s" else ""}",
                        color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { if (s.dailyTarget < 20) vm.setDailyTarget(s.dailyTarget + 1) }) {
                        Icon(Icons.Default.Add, null, tint = AnchorColors.Gold, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // ── General ───────────────────────────────────────────────────────
        Text("General", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        AnchorCard(modifier = Modifier.clickable(onClick = onNavigateToHistory)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = AnchorColors.Gold)
                Spacer(Modifier.width(AnchorSpacing.m))
                Column(Modifier.weight(1f)) {
                    Text("Activity Logs", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                    Text("View your history and follow-through rate", color = AnchorColors.OnBgMuted, fontSize = 12.sp)
                }
                Text("View", color = AnchorColors.Gold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }

        // ── Reminders ─────────────────────────────────────────────────────
        Text("Daily Reminders", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(
            "Scheduled nudges that fire a notification at a set time each day — independent of the automatic trigger system.",
            color = AnchorColors.OnBgMuted, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(AnchorSpacing.s))
        s.reminders.forEach { time ->
            Row(
                Modifier.fillMaxWidth().background(AnchorColors.Surface, RoundedCornerShape(16.dp))
                    .padding(AnchorSpacing.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Schedule, null, tint = AnchorColors.Gold)
                Spacer(Modifier.width(AnchorSpacing.m))
                Text(
                    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
                    color = AnchorColors.OnBg, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.removeReminder(time) }) {
                    Icon(Icons.Default.Delete, null, tint = AnchorColors.OnBgMuted)
                }
            }
        }

        var showTimePicker by remember { mutableStateOf(false) }
        var hour by remember { mutableIntStateOf(12) }
        var minute by remember { mutableIntStateOf(0) }

        if (showTimePicker) {
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                containerColor = AnchorColors.Surface,
                title = { Text("Add Reminder", color = AnchorColors.OnBg, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.s),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Scroll to set the time.",
                            color = AnchorColors.OnBgMuted, fontSize = 13.sp,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ScrollNumberPicker(value = hour, range = 0..23, onValueChange = { hour = it })
                                Text("hr", color = AnchorColors.OnBgMuted, fontSize = 12.sp)
                            }
                            Text(
                                ":",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = AnchorColors.OnBg,
                                modifier = Modifier.padding(horizontal = AnchorSpacing.m).offset(y = (-8).dp),
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ScrollNumberPicker(value = minute, range = 0..59, onValueChange = { minute = it })
                                Text("min", color = AnchorColors.OnBgMuted, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.addReminder(kotlinx.datetime.LocalTime(hour, minute))
                        showTimePicker = false
                    }) { Text("Add", color = AnchorColors.Gold) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                },
            )
        }

        OutlinedButton(
            onClick = { showTimePicker = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(AnchorSpacing.s))
            Text("Add Custom Time")
        }

        // ── Goals ──────────────────────────────────────────────────────────
        Text("Goals", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(
            "Set a multi-session commitment with a deadline. Complete it on time to earn an Exemption Card. Miss it and your streak resets — goals cannot be deleted once created.",
            color = AnchorColors.OnBgMuted, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(AnchorSpacing.s))
        if (g.activeGoal != null) {
            AnchorCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Flag, null, tint = AnchorColors.Gold)
                    Spacer(Modifier.width(AnchorSpacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(g.activeGoal!!.title, color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${g.currentSessions}/${g.activeGoal!!.targetSessions} sessions · ${g.daysRemaining} days left",
                            color = AnchorColors.OnBgMuted, fontSize = 12.sp,
                        )
                    }
                }
            }
        } else {
            Button(
                onClick = { showGoalDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AnchorColors.Gold,
                    contentColor = AnchorColors.Bg,
                ),
            ) {
                Icon(Icons.Default.Flag, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AnchorSpacing.s))
                Text("Set New Goal", fontWeight = FontWeight.Bold)
            }
        }

        // ── Data ───────────────────────────────────────────────────────────
        Text("Data", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(
            "Export a complete backup of your actions, goals, sessions, and settings. Copy or share the JSON, then paste it on another device to restore.",
            color = AnchorColors.OnBgMuted, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(AnchorSpacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.m)) {
            OutlinedButton(
                onClick = { vm.requestExport(); showExportDialog = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AnchorSpacing.s))
                Text("Export")
            }
            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AnchorSpacing.s))
                Text("Import")
            }
        }

        // ── About ──────────────────────────────────────────────────────────────
        HorizontalDivider(color = AnchorColors.Border, modifier = Modifier.padding(vertical = AnchorSpacing.s))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "A work thought of by Joe,\nmade in relation with Jele Sphere.",
                color = AnchorColors.OnBgMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Anchor",
                color = AnchorColors.Border,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Bottom padding for floating nav
        Spacer(Modifier.height(AnchorSpacing.xl))
    }
}

// ── Blocked apps quick-view dialog ───────────────────────────────────────────

@Composable
private fun BlockedAppsListDialog(
    onDismiss: () -> Unit,
    onManage: () -> Unit,
    vm: AppSelectionViewModel = org.koin.compose.viewmodel.koinViewModel(),
) {
    val state by vm.state.collectAsState()

    // Resolve names from loaded app list; fall back to short package name while loading
    val blockedApps = remember(state.apps, state.blockedPackages) {
        if (state.apps.isEmpty()) {
            state.blockedPackages.map { pkg ->
                pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() } to pkg
            }.sortedBy { it.first }
        } else {
            state.apps
                .filter { it.packageName in state.blockedPackages }
                .map { it.name to it.packageName }
                .sortedBy { it.first }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AnchorColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apps, null, tint = AnchorColors.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(AnchorSpacing.s))
                Text("Blocked Apps", color = AnchorColors.OnBg, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.s)) {
                if (blockedApps.isEmpty()) {
                    Text(
                        "No apps are currently blocked.",
                        color = AnchorColors.OnBgMuted,
                        fontSize = 14.sp,
                    )
                } else {
                    Text(
                        "${blockedApps.size} app${if (blockedApps.size != 1) "s" else ""} currently blocked",
                        color = AnchorColors.OnBgMuted,
                        fontSize = 13.sp,
                    )
                    blockedApps.forEach { (name, pkg) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(AnchorColors.SurfaceAlt, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .padding(horizontal = AnchorSpacing.m, vertical = AnchorSpacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                null,
                                tint = AnchorColors.Gold,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(AnchorSpacing.s))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    pkg,
                                    color = AnchorColors.OnBgMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManage) {
                Text("Manage", color = AnchorColors.Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun GoalCreationDialog(
    hasActiveGoal: Boolean,
    onDismiss: () -> Unit,
    onCreate: (title: String, sessions: Int, days: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var targetSessions by remember { mutableIntStateOf(21) }
    var durationDays by remember { mutableIntStateOf(30) }
    val durationOptions = listOf(7, 14, 21, 30, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AnchorColors.Surface,
        title = { Text("New Goal", color = AnchorColors.OnBg, fontWeight = FontWeight.Bold) },
        text = {
            if (hasActiveGoal) {
                Text(
                    "You already have an active goal. Complete or let it expire before creating a new one.",
                    color = AnchorColors.OnBgMuted, fontSize = 14.sp, lineHeight = 20.sp,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.m)) {
                    Text(
                        "Name your goal, set how many sessions you want to complete, and choose a deadline.",
                        color = AnchorColors.OnBgMuted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 40) title = it },
                        placeholder = { Text("e.g. 30-day study habit", color = AnchorColors.OnBgMuted) },
                        label = { Text("Goal title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnchorColors.Gold,
                            unfocusedBorderColor = AnchorColors.Border,
                            focusedTextColor = AnchorColors.OnBg,
                            unfocusedTextColor = AnchorColors.OnBg,
                            focusedContainerColor = AnchorColors.SurfaceAlt,
                            unfocusedContainerColor = AnchorColors.SurfaceAlt,
                        ),
                    )
                    Text("Sessions to complete", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.m),
                    ) {
                        IconButton(onClick = { if (targetSessions > 1) targetSessions-- }) {
                            Icon(Icons.Default.Remove, null, tint = AnchorColors.Gold)
                        }
                        Text(
                            "$targetSessions sessions",
                            color = AnchorColors.OnBg, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(onClick = { if (targetSessions < 365) targetSessions++ }) {
                            Icon(Icons.Default.Add, null, tint = AnchorColors.Gold)
                        }
                    }
                    Text("Deadline", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.s), modifier = Modifier.fillMaxWidth()) {
                        durationOptions.forEach { days ->
                            val selected = days == durationDays
                            Box(
                                Modifier
                                    .background(
                                        if (selected) AnchorColors.Gold else AnchorColors.SurfaceAlt,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { durationDays = days }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${days}d",
                                    color = if (selected) AnchorColors.Bg else AnchorColors.OnBg,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                    Text(
                        "Miss the deadline and your streak resets. Complete it and earn 1 Exemption Card.",
                        color = AnchorColors.OnBgMuted, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
            }
        },
        confirmButton = {
            if (!hasActiveGoal) {
                TextButton(
                    onClick = { if (title.isNotBlank()) onCreate(title, targetSessions, durationDays) },
                    enabled = title.isNotBlank(),
                ) { Text("Create Goal", color = AnchorColors.Gold) }
            } else {
                TextButton(onClick = onDismiss) { Text("OK", color = AnchorColors.Gold) }
            }
        },
        dismissButton = {
            if (!hasActiveGoal) TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Info dialogs ─────────────────────────────────────────────────────────────

@Composable
private fun SensitivityInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AnchorColors.Surface,
        title = { Text("Trigger Sensitivity", color = AnchorColors.OnBg, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.m)) {
                Text(
                    "Sensitivity controls how confident Anchor needs to be before it interrupts you. It's based on your historical session patterns — times of day when you tend to be free.",
                    color = AnchorColors.OnBgMuted, fontSize = 14.sp, lineHeight = 20.sp,
                )
                InfoRow("Rarely", "Only fires when the signal is very strong. Fewer interruptions.")
                InfoRow("Sometimes", "The balanced default. Fires when the signal is reasonably clear.")
                InfoRow("Often", "Fires at the first hint of free time. More prompts, weaker signals.")
                Text(
                    "Tip: start on Sometimes and adjust based on whether the prompts feel well-timed.",
                    color = AnchorColors.Gold, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it", color = AnchorColors.Gold) }
        },
    )
}

@Composable
private fun LevelInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AnchorColors.Surface,
        title = { Text("Intervention Level", color = AnchorColors.OnBg, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.m)) {
                Text(
                    "When Anchor fires, the Intervention Level decides how hard it is to ignore. Sensitivity is when you're interrupted; this is what the interruption looks like.",
                    color = AnchorColors.OnBgMuted, fontSize = 14.sp, lineHeight = 20.sp,
                )
                InfoRow("Gentle", "You receive a notification. Easy to swipe away — relies on willpower.")
                InfoRow("Firm", "A full-screen decision screen appears. You must tap Start, Delay, or Skip before continuing.")
                InfoRow("Strict", "The app you opened is blocked entirely until you make a choice. Most friction, strongest effect.")
                Text(
                    "Tip: most users get the best results on Firm — you have to acknowledge the moment, but you're never truly stuck.",
                    color = AnchorColors.Gold, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it", color = AnchorColors.Gold) }
        },
    )
}

@Composable
private fun InfoRow(label: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = AnchorColors.OnBg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.widthIn(min = 80.dp),
        )
        Spacer(Modifier.width(AnchorSpacing.s))
        Text(description, color = AnchorColors.OnBgMuted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

// ── Time limit card ───────────────────────────────────────────────────────────

@Composable
private fun TimeLimitCard(blockAfterMinutes: Int, onSet: (Int) -> Unit) {
    val isCustom = blockAfterMinutes > 0
    var showCustomDialog by remember { mutableStateOf(false) }
    // Separate hour/minute state for the dialog — pre-populate from current value.
    var dialogHours by remember(blockAfterMinutes) { mutableIntStateOf(blockAfterMinutes / 60) }
    var dialogMinutes by remember(blockAfterMinutes) { mutableIntStateOf(blockAfterMinutes % 60) }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            containerColor = AnchorColors.Surface,
            title = { Text("Set daily time limit", color = AnchorColors.OnBg, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.m)) {
                    Text(
                        "Anchor will only intervene after you've used the blocked app for this long today.",
                        color = AnchorColors.OnBgMuted, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ScrollNumberPicker(value = dialogHours, range = 0..12, onValueChange = { dialogHours = it })
                            Text("hr", color = AnchorColors.OnBgMuted, fontSize = 12.sp)
                        }
                        Text(
                            ":",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = AnchorColors.OnBg,
                            modifier = Modifier.padding(horizontal = AnchorSpacing.m).offset(y = (-8).dp),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ScrollNumberPicker(value = dialogMinutes, range = 0..59, onValueChange = { dialogMinutes = it })
                            Text("min", color = AnchorColors.OnBgMuted, fontSize = 12.sp)
                        }
                    }
                    if (dialogHours == 0 && dialogMinutes == 0) {
                        Text(
                            "Setting both to 0 is the same as Always — blocks on every open.",
                            color = AnchorColors.Gold, fontSize = 12.sp, lineHeight = 16.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSet((dialogHours * 60 + dialogMinutes).coerceAtLeast(0))
                    showCustomDialog = false
                }) { Text("Set", color = AnchorColors.Gold) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") }
            },
        )
    }

    HintBox("Always = block on every open. Custom = choose how many minutes of daily use are allowed before Anchor starts intervening.") {
        AnchorCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = AnchorColors.Gold)
                Spacer(Modifier.width(AnchorSpacing.m))
                Column(Modifier.weight(1f)) {
                    Text("Daily Time Limit", color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (!isCustom) "Block on every open" else "Block after ${blockAfterLabel(blockAfterMinutes)} of use today",
                        color = AnchorColors.OnBgMuted, fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(AnchorSpacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(AnchorSpacing.s)) {
                // Always chip
                Box(
                    Modifier.weight(1f).height(44.dp)
                        .background(
                            if (!isCustom) AnchorColors.Gold else AnchorColors.SurfaceAlt,
                            RoundedCornerShape(22.dp),
                        )
                        .clickable { onSet(0) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Always",
                        color = if (!isCustom) AnchorColors.Bg else AnchorColors.OnBg,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    )
                }
                // Custom chip
                Box(
                    Modifier.weight(1f).height(44.dp)
                        .background(
                            if (isCustom) AnchorColors.Gold else AnchorColors.SurfaceAlt,
                            RoundedCornerShape(22.dp),
                        )
                        .clickable { showCustomDialog = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isCustom) blockAfterLabel(blockAfterMinutes) else "Custom",
                        color = if (isCustom) AnchorColors.Bg else AnchorColors.OnBg,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

// ── Reusable sub-composables ──────────────────────────────────────────────────

/**
 * Drum-roll scroll picker. Scroll up/down to change the value. The centered item is selected
 * and highlighted in gold. The list snaps to the nearest item when you lift your finger.
 */
@Composable
private fun ScrollNumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(range) { range.toList() }
    val itemHeight = 48.dp
    val selectedIndex = (value - range.first).coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val snapFling = rememberSnapFlingBehavior(lazyListState = listState)

    // Report the newly centered item whenever scrolling stops.
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val idx = listState.firstVisibleItemIndex
            onValueChange(items.getOrElse(idx) { value })
        }
    }

    Box(modifier = modifier.width(76.dp)) {
        // Center selection highlight bar
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(AnchorColors.SurfaceAlt, RoundedCornerShape(10.dp))
        )

        LazyColumn(
            state = listState,
            flingBehavior = snapFling,
            modifier = Modifier
                .height(itemHeight * 3)
                .fillMaxWidth(),
            // Padding lets the first and last items scroll to the center position
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(items) { item ->
                val isCentered = item == items.getOrNull(listState.firstVisibleItemIndex)
                Box(
                    Modifier.fillMaxWidth().height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.toString().padStart(2, '0'),
                        fontSize = if (isCentered) 22.sp else 15.sp,
                        fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCentered) AnchorColors.Gold else AnchorColors.OnBgMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Top fade — masks items scrolling off the edge
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(itemHeight)
                .background(Brush.verticalGradient(listOf(AnchorColors.Surface, Color.Transparent)))
        )
        // Bottom fade
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(itemHeight)
                .background(Brush.verticalGradient(listOf(Color.Transparent, AnchorColors.Surface)))
        )
    }
}

@Composable
private fun ToggleCard(icon: ImageVector, title: String, body: String, value: Boolean, onChange: (Boolean) -> Unit) {
    AnchorCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AnchorColors.Gold)
            Spacer(Modifier.width(AnchorSpacing.m))
            Column(Modifier.weight(1f)) {
                Text(title, color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                Text(body, color = AnchorColors.OnBgMuted, fontSize = 13.sp)
            }
            Switch(
                checked = value, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AnchorColors.OnBg,
                    checkedTrackColor = AnchorColors.Gold,
                    uncheckedTrackColor = AnchorColors.SurfaceAlt,
                ),
            )
        }
    }
}
