package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset

/**
 * Premium ambient gradient + precision grid, rendered natively by Compose.
 *
 * Draw-only caching avoids an always-running animation loop, WebView layer,
 * GPU blur, timer, or retained bitmap. This is intentionally static on ARM32;
 * motion is confined to short, user-triggered focus transitions.
 * High-contrast mode suppresses decorative layers entirely.
 */
@Composable
fun PremiumGridBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = colors.background == BousygramThemePreset.HIGH_CONTRAST.background
    val light = colors.background == BousygramThemePreset.LIGHT.background

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        if (!highContrast) {
            Box(Modifier.matchParentSize().drawWithCache {
                val spacing = 52.dp.toPx()
                val hairline = 1.dp.toPx()
                val gridColor = colors.onBackground.copy(alpha = if (light) 0.045f else 0.055f)
                val primaryGlow = Brush.radialGradient(
                    colors = listOf(colors.primary.copy(alpha = if (light) 0.055f else 0.13f),
                        Color.Transparent),
                    center = Offset(size.width * 0.81f, size.height * 0.14f),
                    radius = maxOf(size.width, size.height) * 0.85f
                )
                val secondaryGlow = Brush.radialGradient(
                    colors = listOf(colors.secondary.copy(alpha = if (light) 0.045f else 0.10f),
                        Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 0.9f),
                    radius = maxOf(size.width, size.height) * 0.72f
                )
                onDrawBehind {
                    drawRect(primaryGlow)
                    drawRect(secondaryGlow)
                    repeat((size.width / spacing).toInt() + 1) { index ->
                        val x = index * spacing
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), hairline)
                    }
                    repeat((size.height / spacing).toInt() + 1) { index ->
                        val y = index * spacing
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), hairline)
                    }
                }
            })
        }
        content()
    }
}
