package com.anchor.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorSpacing
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppSelectionScreen(
    onBack: () -> Unit,
    vm: AppSelectionViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }

    val visibleApps = remember(state.apps, query) {
        if (query.isBlank()) state.apps
        else state.apps.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Column(Modifier.padding(horizontal = AnchorSpacing.xl)) {
            Spacer(Modifier.height(AnchorSpacing.xl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AnchorColors.OnBg)
                }
                Text(
                    "Blocked Apps",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnchorColors.OnBg,
                    modifier = Modifier.weight(1f),
                )
                if (state.apps.isNotEmpty()) {
                    Text(
                        "${state.blockedPackages.size} blocked",
                        color = AnchorColors.OnBgMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(AnchorSpacing.m))

            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps…", color = AnchorColors.OnBgMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = AnchorColors.OnBgMuted)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, null, tint = AnchorColors.OnBgMuted)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AnchorColors.Gold,
                    unfocusedBorderColor = AnchorColors.Border,
                    focusedContainerColor = AnchorColors.Surface,
                    unfocusedContainerColor = AnchorColors.Surface,
                    cursorColor = AnchorColors.Gold,
                    focusedTextColor = AnchorColors.OnBg,
                    unfocusedTextColor = AnchorColors.OnBg,
                ),
            )
            Spacer(Modifier.height(AnchorSpacing.m))
        }

        // ── App list ─────────────────────────────────────────────────────────
        when {
            state.apps.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading apps…", color = AnchorColors.OnBgMuted)
                }
            }
            visibleApps.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No apps match \"$query\"", color = AnchorColors.OnBgMuted)
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = AnchorSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(AnchorSpacing.m),
                    contentPadding = PaddingValues(bottom = AnchorSpacing.xl),
                ) {
                    items(visibleApps, key = { it.packageName }) { app ->
                        val isBlocked = state.blockedPackages.contains(app.packageName)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(AnchorColors.Surface, RoundedCornerShape(16.dp))
                                .clickable { vm.toggleApp(app.packageName) }
                                .padding(AnchorSpacing.l),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(app.name, color = AnchorColors.OnBg, fontWeight = FontWeight.SemiBold)
                                Text(
                                    app.packageName,
                                    color = AnchorColors.OnBgMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            Switch(
                                checked = isBlocked,
                                onCheckedChange = { vm.toggleApp(app.packageName) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AnchorColors.OnBg,
                                    checkedTrackColor = AnchorColors.Gold,
                                    uncheckedTrackColor = AnchorColors.SurfaceAlt,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
