package com.anchor.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AnchorColors {
    val Bg = Color(0xFF0B0B0C)
    val Surface = Color(0xFF161618)
    val SurfaceAlt = Color(0xFF1E1E22)
    val Border = Color(0xFF2A2A2F)
    val OnBg = Color(0xFFF2EFE7)
    val OnBgMuted = Color(0xFF8C8A86)
    val Gold = Color(0xFFB59E6A)
    val GoldMuted = Color(0xFF6E5F40)
    val Danger = Color(0xFFE26A6A)
    val DangerMuted = Color(0xFF5C2E2E)
}

object AnchorSpacing {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp
}

@Composable
fun AnchorTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        background = AnchorColors.Bg,
        surface = AnchorColors.Surface,
        surfaceVariant = AnchorColors.SurfaceAlt,
        primary = AnchorColors.Gold,
        onPrimary = Color(0xFF1A140A),
        onBackground = AnchorColors.OnBg,
        onSurface = AnchorColors.OnBg,
        outline = AnchorColors.Border,
        error = AnchorColors.Danger,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
