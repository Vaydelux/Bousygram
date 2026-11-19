package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors

/**
 * Subtle theme-aware Cinema Halo ambient background, rendered natively by Compose.
 *
 * This legacy component name is retained for compatibility with callers.
 * Draw-only caching avoids an always-running animation loop, WebView layer,
 * GPU blur, timer, or retained bitmap — safe on 32-bit/low-RAM Android TVs.
 * High-contrast mode suppresses decorative layers entirely.
 */
@Composable
fun PremiumGridBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val highContrast = colors.background == BousygramThemePreset.HIGH_CONTRAST.background
    val light = colors.background == BousygramThemePreset.LIGHT.background ||
        colors.background == BousygramThemePreset.IVORY.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("bousygram-premium-backdrop")
    ) {
        if (!highContrast) {
            Box(Modifier.matchParentSize().drawWithCache {
                val maxDim = maxOf(size.width, size.height)
                val primaryGlow = Brush.radialGradient(
                    colors = listOf(
                        halo.haloStart.copy(alpha = if (light) 0.06f else 0.14f),
                        halo.haloEnd.copy(alpha = if (light) 0.025f else 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.84f, size.height * 0.10f),
                    radius = maxDim * 0.82f
                )
                val secondaryGlow = Brush.radialGradient(
                    colors = listOf(
                        halo.haloEnd.copy(alpha = if (light) 0.045f else 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.08f, size.height * 0.92f),
                    radius = maxDim * 0.70f
                )
                val warmAccent = Brush.radialGradient(
                    colors = listOf(
                        halo.goldAccent.copy(alpha = if (light) 0.025f else 0.045f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.50f, size.height * 0.02f),
                    radius = maxDim * 0.48f
                )
                onDrawBehind {
                    drawRect(primaryGlow)
                    drawRect(secondaryGlow)
                    drawRect(warmAccent)
                }
            })
        }
        content()
    }
}
