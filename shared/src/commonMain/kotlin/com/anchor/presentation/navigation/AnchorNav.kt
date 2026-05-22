package com.anchor.presentation.navigation

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import com.anchor.domain.repository.SettingsRepository
import com.anchor.platform.hasShownCoachMark
import com.anchor.platform.setCoachMarkShown
import com.anchor.presentation.coachmark.CoachMarkKeys
import com.anchor.presentation.coachmark.CoachMarkOverlay
import com.anchor.presentation.coachmark.coachMarkTarget
import com.anchor.presentation.history.HistoryScreen
import com.anchor.presentation.home.HomeScreen
import com.anchor.presentation.intervention.InterventionScreen
import com.anchor.presentation.onboarding.OnboardingActionsScreen
import com.anchor.presentation.onboarding.OnboardingPreviewScreen
import com.anchor.presentation.onboarding.OnboardingWelcomeScreen
import com.anchor.presentation.permissions.PermissionsScreen
import com.anchor.presentation.session.SessionScreen
import com.anchor.presentation.settings.AppSelectionScreen
import com.anchor.presentation.settings.SettingsScreen
import com.anchor.presentation.stats.StatsScreen
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorTheme

sealed interface Screen {
    data object Welcome : Screen
    data object PickActions : Screen
    data object Preview : Screen
    data object Permissions : Screen
    data object Main : Screen
    data class Intervention(val triggerId: String, val onCloseTarget: Screen = Main) : Screen
    data class Session(val sessionId: String, val onDoneTarget: Screen = Main) : Screen
    data object AppSelection : Screen
    data object History : Screen
}

enum class Tab(val icon: ImageVector, val label: String) {
    Home(Icons.Default.Home, "Home"),
    Stats(Icons.Default.BarChart, "Stats"),
    Settings(Icons.Default.Settings, "Settings");
}

/** Height reserved at the bottom for the floating navbar + its padding. */
private val FLOAT_NAV_HEIGHT = 90.dp

@Composable
fun AnchorNav(
    settings: SettingsRepository = org.koin.compose.koinInject<SettingsRepository>(),
    deepLink: String? = null,
) {
    var screen by remember { mutableStateOf<Screen?>(null) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var navigationStack by remember { mutableStateOf<List<Screen>>(emptyList()) }
    var initialized by remember { mutableStateOf(false) }
    var showCoachMark by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val completed = settings.onboardingCompleted.first()
        val start: Screen = when {
            !completed -> Screen.Welcome
            deepLink != null && deepLink.startsWith("intervention://") -> {
                val triggerId = deepLink.removePrefix("intervention://").ifBlank { "external" }
                Screen.Intervention(triggerId)
            }
            else -> Screen.Main
        }
        screen = start
        navigationStack = listOf(start)
        initialized = true
    }

    // Show coach mark once, on first entry to Main after onboarding
    LaunchedEffect(initialized) {
        if (!initialized) return@LaunchedEffect
        val completed = settings.onboardingCompleted.first()
        if (completed && !hasShownCoachMark()) {
            delay(700) // one extra frame after layout settles
            showCoachMark = true
        }
    }

    LaunchedEffect(deepLink) {
        if (!initialized || deepLink == null) return@LaunchedEffect
        if (!deepLink.startsWith("intervention://")) return@LaunchedEffect
        val completed = settings.onboardingCompleted.first()
        if (!completed) return@LaunchedEffect
        val triggerId = deepLink.removePrefix("intervention://").ifBlank { "external" }
        val target = Screen.Intervention(triggerId)
        screen = target
        navigationStack = navigationStack + target
    }

    val currentScreen = screen

    BackHandler(enabled = navigationStack.size > 1) {
        navigationStack = navigationStack.dropLast(1)
        screen = navigationStack.lastOrNull() ?: Screen.Main
    }

    AnchorTheme {
        Surface(color = AnchorColors.Bg, modifier = Modifier.fillMaxSize()) {
            when (val s = currentScreen ?: return@Surface) {
                is Screen.Welcome -> OnboardingWelcomeScreen {
                    screen = Screen.PickActions
                    navigationStack = navigationStack + Screen.PickActions
                }
                is Screen.PickActions -> OnboardingActionsScreen(onContinue = {
                    screen = Screen.Preview
                    navigationStack = navigationStack + Screen.Preview
                })
                is Screen.Preview -> OnboardingPreviewScreen(
                    onTryIntervention = {
                        screen = Screen.Intervention("preview-trigger", Screen.Permissions)
                        navigationStack = navigationStack + Screen.Intervention("preview-trigger", Screen.Permissions)
                    },
                    onContinue = {
                        screen = Screen.Permissions
                        navigationStack = navigationStack + Screen.Permissions
                    },
                )
                is Screen.Permissions -> PermissionsScreen(onFinish = {
                    screen = Screen.Main
                    navigationStack = navigationStack + Screen.Main
                })

                is Screen.Main -> Box(Modifier.fillMaxSize()) {
                    // Tab content — padded so it doesn't hide under the floating bar.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = FLOAT_NAV_HEIGHT),
                    ) {
                        when (tab) {
                            Tab.Home -> HomeScreen(
                                onOpenIntervention = {
                                    screen = Screen.Intervention(it)
                                    navigationStack = navigationStack + Screen.Intervention(it)
                                },
                                onSessionStarted = {
                                    screen = Screen.Session(it)
                                    navigationStack = navigationStack + Screen.Session(it)
                                },
                                onNavigateToSettings = { tab = Tab.Settings },
                            )
                            Tab.Stats -> StatsScreen()
                            Tab.Settings -> SettingsScreen(
                                onNavigateToBlockedApps = {
                                    screen = Screen.AppSelection
                                    navigationStack = navigationStack + Screen.AppSelection
                                },
                                onNavigateToHistory = {
                                    screen = Screen.History
                                    navigationStack = navigationStack + Screen.History
                                },
                            )
                        }
                    }

                    // Premium floating dock
                    FloatingDock(
                        current = tab,
                        onSelect = { tab = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                    )

                    // Coach mark overlay — shown once after first launch
                    if (showCoachMark) {
                        CoachMarkOverlay(
                            onComplete = {
                                showCoachMark = false
                                setCoachMarkShown()
                            },
                            onTabRequired = { tabKey ->
                                tab = when (tabKey) {
                                    "stats"    -> Tab.Stats
                                    "settings" -> Tab.Settings
                                    else       -> Tab.Home
                                }
                            },
                        )
                    }
                }

                is Screen.Intervention -> InterventionScreen(
                    triggerId = s.triggerId,
                    onClose = {
                        screen = s.onCloseTarget
                        navigationStack = navigationStack.dropLast(1)
                    },
                    onSessionStarted = {
                        screen = Screen.Session(it, s.onCloseTarget)
                        navigationStack = navigationStack + Screen.Session(it, s.onCloseTarget)
                    },
                    onNavigateToSettings = {
                        screen = Screen.Main
                        tab = Tab.Settings
                        navigationStack = listOf(Screen.Main)
                    },
                )
                is Screen.Session -> SessionScreen(s.sessionId, onDone = {
                    screen = s.onDoneTarget
                    navigationStack = navigationStack.dropLast(1)
                })
                is Screen.AppSelection -> AppSelectionScreen(onBack = {
                    screen = Screen.Main
                    navigationStack = navigationStack.dropLast(1)
                })
                is Screen.History -> HistoryScreen(onBack = {
                    screen = Screen.Main
                    navigationStack = navigationStack.dropLast(1)
                })
            }
        }
    }
}

// ── Premium floating dock ─────────────────────────────────────────────────────

/**
 * A capsule-shaped floating navigation dock with three tabs: Home, Stats, Settings.
 *  • Layered dark glass backgrounds for depth
 *  • Ambient gold glow via coloured shadow
 *  • Per-tab spring-animated icon scale + animated dot indicator
 *  • Fluid animated tab transitions
 */
@Composable
private fun FloatingDock(
    current: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val capsuleShape = RoundedCornerShape(40.dp)

    Row(
        modifier = modifier
            .shadow(
                elevation = 28.dp,
                shape = capsuleShape,
                clip = false,
                ambientColor = AnchorColors.Gold.copy(alpha = 0.28f),
                spotColor    = AnchorColors.Gold.copy(alpha = 0.50f),
            )
            .shadow(elevation = 10.dp, shape = capsuleShape, clip = false)
            .clip(capsuleShape)
            .background(Color(0xF4141414))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0x0FD4AF37),
                        Color(0x18D4AF37),
                        Color(0x0FD4AF37),
                    )
                )
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockTab(tab = Tab.Home,     selected = current == Tab.Home,     onSelect = onSelect, coachKey = CoachMarkKeys.NAV_HOME)
        DockTab(tab = Tab.Stats,    selected = current == Tab.Stats,    onSelect = onSelect, coachKey = CoachMarkKeys.NAV_STATS)
        DockTab(tab = Tab.Settings, selected = current == Tab.Settings, onSelect = onSelect, coachKey = CoachMarkKeys.NAV_SETTINGS)
    }
}

// ── Tab item ──────────────────────────────────────────────────────────────────

@Composable
private fun RowScope.DockTab(
    tab: Tab,
    selected: Boolean,
    onSelect: (Tab) -> Unit,
    coachKey: String,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "iconScale_${tab.name}",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) AnchorColors.Gold else Color(0xFF777777),
        animationSpec = tween(durationMillis = 200),
        label = "iconTint_${tab.name}",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.55f,
        animationSpec = tween(durationMillis = 200),
        label = "labelAlpha_${tab.name}",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .coachMarkTarget(coachKey)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onSelect(tab) },
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Icon + optional glow background
        Box(contentAlignment = Alignment.Center) {
            // Glow halo behind selected icon
            if (selected) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    AnchorColors.Gold.copy(alpha = 0.20f),
                                    Color.Transparent,
                                )
                            ),
                            CircleShape,
                        )
                )
            }

            // Active pill behind icon
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        if (selected) AnchorColors.Gold.copy(alpha = 0.15f) else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector     = tab.icon,
                    contentDescription = tab.label,
                    tint            = iconTint,
                    modifier        = Modifier
                        .size(22.dp)
                        .scale(iconScale),
                )
            }
        }

        // Label
        Text(
            text     = tab.label,
            color    = iconTint,
            fontSize = 10.sp,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
        )

        // Tiny dot indicator
        AnimatedVisibility(
            visible = selected,
            enter   = scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f)) + fadeIn(),
            exit    = scaleOut(tween(100)) + fadeOut(tween(100)),
        ) {
            Box(
                Modifier
                    .size(4.dp)
                    .background(AnchorColors.Gold, CircleShape)
            )
        }
    }
}

