package com.anchor.presentation.coachmark

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.presentation.theme.AnchorColors
import kotlinx.coroutines.delay

// ── Keys ─────────────────────────────────────────────────────────────────────

object CoachMarkKeys {
    // No-spotlight bookend steps
    const val WELCOME           = "welcome"
    const val DONE              = "done"
    // Home tab
    const val HOME_PROGRESS     = "home_progress"
    const val HOME_START_NOW    = "home_start_now"
    const val HOME_TRIGGER      = "home_trigger"
    // Nav tabs (always on-screen)
    const val NAV_HOME          = "nav_home"
    const val NAV_STATS         = "nav_stats"
    const val NAV_SETTINGS      = "nav_settings"
    // Stats tab
    const val STATS_RATE        = "stats_rate"
    const val STATS_STREAK      = "stats_streak"
    const val STATS_GOALS       = "stats_goals"
    // Settings tab
    const val SETTINGS_TRIGGERS = "settings_triggers"
}

// ── Step model ────────────────────────────────────────────────────────────────

/**
 * @param requiresTab  If non-null, the tour will switch to that tab string
 *                     ("home" | "stats" | "settings") before rendering this step.
 *                     The switch is requested from [CoachMarkOverlay] via [onTabRequired].
 */
data class CoachMarkStep(
    val key: String,
    val title: String,
    val description: String,
    val spotlight: Boolean = true,
    val requiresTab: String? = null,
)

val ANCHOR_COACH_STEPS: List<CoachMarkStep> = listOf(

    // ── Intro ────────────────────────────────────────────────────────────────
    CoachMarkStep(
        key = CoachMarkKeys.WELCOME,
        title = "Welcome to Anchor",
        description = "A quick tour of every feature. Tap Next to see how the app works — or Skip to dive right in.",
        spotlight = false,
    ),

    // ── Home tab ─────────────────────────────────────────────────────────────
    CoachMarkStep(
        key = CoachMarkKeys.HOME_PROGRESS,
        title = "Your Daily Progress",
        description = "This card tracks how many sessions you've completed today vs. your daily target. The bar fills as you stay consistent.",
        requiresTab = "home",
    ),
    CoachMarkStep(
        key = CoachMarkKeys.HOME_START_NOW,
        title = "Start a Focus Session",
        description = "Tap any activity button to begin a timed session immediately. You can add or remove activities in Settings.",
        requiresTab = "home",
    ),
    CoachMarkStep(
        key = CoachMarkKeys.HOME_TRIGGER,
        title = "Smart Triggers",
        description = "Anchor watches your usage patterns and detects natural gaps in your day — moments when you're likely free to focus. Tap the card to open an intervention right now.",
        requiresTab = "home",
    ),

    // ── Stats tab — spotlight nav tab first, then switch ─────────────────────
    CoachMarkStep(
        key = CoachMarkKeys.NAV_STATS,
        title = "Progress & Stats",
        description = "Your consistency metrics, weekly activity, and goal progress all live here. Let's take a look.",
    ),
    CoachMarkStep(
        key = CoachMarkKeys.STATS_RATE,
        title = "Follow-Through Rate",
        description = "The percentage of Anchor prompts you acted on in the last 7 days. The higher, the more consistently you're showing up when it counts.",
        requiresTab = "stats",
    ),
    CoachMarkStep(
        key = CoachMarkKeys.STATS_GOALS,
        title = "Goals",
        description = "Set a multi-session commitment with a deadline. Complete it on time to earn an Exemption Card. Miss it and your streak resets. Goals cannot be deleted once created.",
        requiresTab = "stats",
    ),
    CoachMarkStep(
        key = CoachMarkKeys.STATS_STREAK,
        title = "Streaks & Activity",
        description = "Consecutive days with at least one completed session. Miss a day and your streak resets — so show up every day, even for a short session.",
        requiresTab = "stats",
    ),

    // ── Settings tab — spotlight nav tab first, then switch ──────────────────
    CoachMarkStep(
        key = CoachMarkKeys.NAV_SETTINGS,
        title = "Settings & Configuration",
        description = "This is where you tune everything — your focus activities, how often Anchor fires, session length, reminders, and data backup.",
    ),
    CoachMarkStep(
        key = CoachMarkKeys.SETTINGS_TRIGGERS,
        title = "Trigger Sensitivity",
        description = "Controls how often Anchor interrupts you. 'Rarely' fires only on strong signals; 'Often' fires at the first hint of free time. Start on 'Sometimes' and adjust from there.",
        requiresTab = "settings",
    ),

    // ── Outro ─────────────────────────────────────────────────────────────────
    CoachMarkStep(
        key = CoachMarkKeys.DONE,
        title = "You're Ready!",
        description = "Anchor will prompt you when it detects drift windows. Complete sessions, build your streak, and watch your Follow-Through Rate climb.",
        spotlight = false,
    ),
)

// ── Controller ───────────────────────────────────────────────────────────────

object CoachMarkController {
    private val targets = mutableStateMapOf<String, Rect>()
    fun register(key: String, bounds: Rect) { targets[key] = bounds }
    fun getBounds(key: String): Rect? = targets[key]
}

fun Modifier.coachMarkTarget(key: String): Modifier = this.onGloballyPositioned { coords ->
    val b = coords.boundsInRoot()
    CoachMarkController.register(key, Rect(b.left, b.top, b.right, b.bottom))
}

// ── Overlay composable ────────────────────────────────────────────────────────

/**
 * Full-screen coach-mark overlay.
 *
 * - Punches a circular spotlight through a dark backdrop over the target element.
 * - The tooltip card appears **above** the spotlight when the element is in the
 *   lower half of the screen, and **below** it when in the upper half — so it
 *   never covers what it is describing.
 * - Steps that [CoachMarkStep.requiresTab] request a tab switch are routed through
 *   [onTabRequired] before the step is shown, giving layout time to settle.
 *
 * @param onTabRequired  Called with "home" | "stats" | "settings" before a step
 *                       that requires a tab switch is shown.
 */
@Composable
fun CoachMarkOverlay(
    steps: List<CoachMarkStep> = ANCHOR_COACH_STEPS,
    onComplete: () -> Unit,
    onTabRequired: ((String) -> Unit)? = null,
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    // Delays the spotlight render after a tab switch so layout can settle.
    var spotlightReady by remember { mutableStateOf(true) }

    if (stepIndex >= steps.size) {
        LaunchedEffect(Unit) { onComplete() }
        return
    }

    val step = steps[stepIndex]
    val isLast = stepIndex == steps.lastIndex
    val bounds: Rect? = if (step.spotlight) CoachMarkController.getBounds(step.key) else null

    // When the step index changes due to a tab switch, hide the spotlight briefly
    // so the spring doesn't animate from the old position on the wrong tab.
    LaunchedEffect(stepIndex) {
        val s = steps.getOrElse(stepIndex) { return@LaunchedEffect }
        if (s.requiresTab != null) {
            spotlightReady = false
            delay(400) // allow layout to settle after tab switch + recomposition
            spotlightReady = true
        } else {
            spotlightReady = true
        }
    }

    // Animate spotlight position smoothly
    val spotCx by animateFloatAsState(
        targetValue = if (spotlightReady) bounds?.center?.x ?: 0f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 280f),
        label = "spotCx",
    )
    val spotCy by animateFloatAsState(
        targetValue = if (spotlightReady) bounds?.center?.y ?: 0f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 280f),
        label = "spotCy",
    )
    val spotRadius by animateFloatAsState(
        targetValue = if (spotlightReady && bounds != null) maxOf(bounds.width, bounds.height) / 2f + 36f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "spotRadius",
    )

    // Dynamic card placement: above spotlight when element is in lower screen half.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        val screenWidthPx = constraints.maxWidth.toFloat()
        val margin = spotRadius.coerceAtLeast(32f)

        // Clamp to full screen bounds so spotlights that land off-screen (elements below
        // the fold in scrollable containers) are pulled back into view, while nav tab
        // spotlights (which sit inside the floating dock area at the bottom) are untouched.
        val drawCx = if (step.spotlight && bounds != null)
            spotCx.coerceIn(margin, screenWidthPx - margin) else spotCx
        val drawCy = if (step.spotlight && bounds != null)
            spotCy.coerceIn(margin, screenHeightPx - margin) else spotCy

        val cardAboveSpotlight = step.spotlight && bounds != null && drawCy > screenHeightPx * 0.52f

        // ── Layer 1: dark overlay with cutout ─────────────────────────────────
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(Color.Black.copy(alpha = 0.78f))
            if (step.spotlight && spotRadius > 0f) {
                drawCircle(
                    color = Color.Black,
                    center = Offset(drawCx, drawCy),
                    radius = spotRadius,
                    blendMode = BlendMode.Clear,
                )
            }
        }

        // ── Layer 2: gold glow ring ───────────────────────────────────────────
        if (step.spotlight && spotRadius > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(drawCx, drawCy)
                val outerRadius = spotRadius + 60f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AnchorColors.Gold.copy(alpha = 0.30f), Color.Transparent),
                        center = center,
                        radius = outerRadius,
                    ),
                    center = center,
                    radius = outerRadius,
                )
                drawCircle(
                    color = AnchorColors.Gold.copy(alpha = 0.65f),
                    center = center,
                    radius = spotRadius + 4f,
                    style = Stroke(width = 2.5f),
                )
            }
        }

        // ── Layer 3: tooltip card ─────────────────────────────────────────────
        // Positioned on the opposite side of the screen from the spotlight so it
        // never covers the highlighted element.
        AnimatedContent(
            targetState = stepIndex,
            transitionSpec = {
                (slideInHorizontally { it / 5 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 5 } + fadeOut())
            },
            modifier = Modifier
                .align(if (cardAboveSpotlight) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (cardAboveSpotlight) 28.dp else 0.dp,
                    bottom = if (!cardAboveSpotlight) 28.dp else 0.dp,
                ),
            label = "stepCard",
        ) { idx ->
            val s = steps.getOrElse(idx) { step }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = AnchorColors.Surface,
                shadowElevation = 20.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Step indicator dots + skip button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        steps.forEachIndexed { i, _ ->
                            Box(
                                Modifier
                                    .height(6.dp)
                                    .width(if (i == idx) 20.dp else 6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (i == idx) AnchorColors.Gold else AnchorColors.Border),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = onComplete,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text("Skip tour", color = AnchorColors.OnBgMuted, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(s.title, color = AnchorColors.OnBg, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(s.description, color = AnchorColors.OnBgMuted, fontSize = 15.sp, lineHeight = 22.sp)

                    Spacer(Modifier.height(6.dp))

                    Button(
                        onClick = {
                            if (isLast) {
                                onComplete()
                            } else {
                                // Request tab switch for the *next* step before advancing,
                                // so the tab changes in the same recomposition.
                                val nextStep = steps.getOrNull(idx + 1)
                                nextStep?.requiresTab?.let { onTabRequired?.invoke(it) }
                                stepIndex++
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AnchorColors.Gold,
                            contentColor = AnchorColors.Bg,
                        ),
                    ) {
                        Text(
                            if (isLast) "Get Started" else "Next",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}
