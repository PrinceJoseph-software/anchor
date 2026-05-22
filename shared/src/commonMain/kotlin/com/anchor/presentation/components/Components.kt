package com.anchor.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.presentation.theme.AnchorColors
import com.anchor.presentation.theme.AnchorSpacing

/**
 * Wraps [content] in a Material3 plain tooltip shown on long-press.
 * Use this to give first-time users context on any confusing UI element
 * without cluttering the screen with permanent labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HintBox(
    hint: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                containerColor = AnchorColors.SurfaceAlt,
                contentColor = AnchorColors.OnBg,
            ) {
                Text(
                    hint,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
fun AnchorCard(
    modifier: Modifier = Modifier,
    border: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AnchorColors.Surface,
        border = if (border) BorderStroke(1.dp, AnchorColors.Border) else null,
    ) {
        Column(Modifier.padding(AnchorSpacing.l), content = content)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AnchorColors.OnBg,
            contentColor = Color(0xFF111111),
            disabledContainerColor = AnchorColors.SurfaceAlt,
            disabledContentColor = AnchorColors.OnBgMuted,
        ),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AnchorColors.Gold,
            contentColor = Color(0xFF1A140A),
            disabledContainerColor = AnchorColors.SurfaceAlt,
            disabledContentColor = AnchorColors.OnBgMuted,
        ),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}
