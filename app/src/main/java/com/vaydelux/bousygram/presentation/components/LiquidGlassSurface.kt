package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.theme.BousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramReduceTransparency
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy

/**
 * Elevation & translucency hierarchy for Bousygram's iOS 27/28 Liquid Glass
 * material system. Every level remains 100% API 26-37 and 32-bit TV safe
 * (zero GPU RenderEffect stalls or retained bitmaps) and automatically switches
 * to crisp high-legibility solid surfaces when Reduce Transparency or High
 * Contrast is enabled.
 */
enum class BousygramGlassLevel {
    /** Quiet secondary containers, pill tracks, and inset segmented trays. */
    SUBTLE,
    /** Media cards, folder tiles, and interactive list surfaces. */
    CARD,
    /** Main detached glass panels, settings groups, and auth stations. */
    PANEL,
    /** Detached floating top bars, side rails, and bottom capsule docks. */
    FLOATING_CHROME,
    /** High-contrast obsidian Dynamic Island capsule (stays dark even in Light Mode). */
    DYNAMIC_ISLAND,
    /** Expanded Live Activity sheets, Appearance Popover, and player menus. */
    POPOVER
}

@Immutable
internal data class BousygramGlassMaterial(
    val fill: Color,
    val specularTop: Color,
    val specularBottom: Color,
    val rimTop: Color,
    val rimBottom: Color,
    val borderWidth: Dp,
    val shadowElevation: Dp,
    val showSpecularSheen: Boolean
)

/**
 * Pure opacity policy verified by unit tests. Guarantees readable contrast in
 * translucent mode while honoring iOS 27 Reduce Transparency and High Contrast.
 */
internal fun resolveLiquidGlassAlpha(
    level: BousygramGlassLevel,
    isLight: Boolean,
    isHighContrast: Boolean,
    reduceTransparency: Boolean
): Float {
    if (isHighContrast) return 0.99f
    if (reduceTransparency) return if (level == BousygramGlassLevel.DYNAMIC_ISLAND) 0.98f else 0.97f
    return when (level) {
        BousygramGlassLevel.SUBTLE -> if (isLight) 0.72f else 0.64f
        BousygramGlassLevel.CARD -> if (isLight) 0.84f else 0.76f
        BousygramGlassLevel.PANEL -> if (isLight) 0.86f else 0.78f
        BousygramGlassLevel.FLOATING_CHROME -> if (isLight) 0.88f else 0.82f
        BousygramGlassLevel.DYNAMIC_ISLAND -> 0.92f
        BousygramGlassLevel.POPOVER -> if (isLight) 0.92f else 0.88f
    }
}

internal fun resolveGlassMaterial(
    level: BousygramGlassLevel,
    colors: ColorScheme,
    halo: BousygramHaloColors,
    reduceTransparency: Boolean,
    lowRam: Boolean,
    accentTinted: Boolean = false
): BousygramGlassMaterial {
    val alpha = resolveLiquidGlassAlpha(level, halo.isLight, halo.isHighContrast, reduceTransparency)
    val solid = halo.isHighContrast || reduceTransparency

    if (level == BousygramGlassLevel.DYNAMIC_ISLAND) {
        val obsidianBase = if (halo.isHighContrast) colors.surfaceContainerHighest else Color(0xFF090D16)
        val fill = obsidianBase.copy(alpha = alpha)
        val rimTop = if (halo.isHighContrast) colors.primary
        else Color.White.copy(alpha = if (reduceTransparency) 0.24f else 0.28f)
        val rimBottom = if (halo.isHighContrast) colors.outline
        else Color.White.copy(alpha = if (reduceTransparency) 0.10f else 0.08f)
        return BousygramGlassMaterial(
            fill = fill,
            specularTop = Color.White.copy(alpha = if (solid) 0f else 0.11f),
            specularBottom = Color.Transparent,
            rimTop = rimTop,
            rimBottom = rimBottom,
            borderWidth = if (halo.isHighContrast) 1.5.dp else 1.dp,
            shadowElevation = if (lowRam || halo.isHighContrast) 0.dp else 12.dp,
            showSpecularSheen = !solid
        )
    }

    val rawSurface = when (level) {
        BousygramGlassLevel.SUBTLE -> if (halo.isLight) colors.surfaceContainer else colors.surfaceContainerLow
        BousygramGlassLevel.CARD -> if (halo.isLight) colors.surface else colors.surfaceContainer
        BousygramGlassLevel.PANEL -> if (halo.isLight) colors.surface else colors.surfaceContainer
        BousygramGlassLevel.FLOATING_CHROME -> if (halo.isLight) colors.surface else colors.surfaceContainerHigh
        BousygramGlassLevel.POPOVER -> if (halo.isLight) colors.surface else colors.surfaceContainerHigh
        BousygramGlassLevel.DYNAMIC_ISLAND -> Color(0xFF090D16)
    }

    val tintedBase = if (accentTinted && !halo.isHighContrast) {
        lerp(rawSurface, colors.primaryContainer, if (halo.isLight) 0.24f else 0.34f)
    } else if (!halo.isLight && !solid) {
        // Subtle cool specular wash keeps dark glass crisp over deep obsidian aurora
        lerp(rawSurface, colors.primary, 0.03f)
    } else {
        rawSurface
    }

    val rimTop = when {
        halo.isHighContrast -> colors.outline
        accentTinted -> colors.primary.copy(alpha = if (halo.isLight) 0.42f else 0.52f)
        halo.isLight -> Color.White.copy(alpha = if (reduceTransparency) 0.90f else 0.92f)
        else -> Color.White.copy(alpha = if (reduceTransparency) 0.20f else 0.22f)
    }
    val rimBottom = when {
        halo.isHighContrast -> colors.outline
        accentTinted -> colors.primary.copy(alpha = if (halo.isLight) 0.20f else 0.22f)
        halo.isLight -> Color(0xFF0F172A).copy(alpha = if (reduceTransparency) 0.14f else 0.09f)
        else -> Color.White.copy(alpha = if (reduceTransparency) 0.09f else 0.06f)
    }

    val elevation = when {
        lowRam || halo.isHighContrast -> 0.dp
        level == BousygramGlassLevel.POPOVER -> 14.dp
        level == BousygramGlassLevel.FLOATING_CHROME -> 10.dp
        level == BousygramGlassLevel.PANEL -> 5.dp
        level == BousygramGlassLevel.CARD -> 2.dp
        else -> 0.dp
    }

    return BousygramGlassMaterial(
        fill = tintedBase.copy(alpha = alpha),
        specularTop = if (halo.isLight) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.09f),
        specularBottom = Color.Transparent,
        rimTop = rimTop,
        rimBottom = rimBottom,
        borderWidth = if (halo.isHighContrast) 1.5.dp else 1.dp,
        shadowElevation = elevation,
        showSpecularSheen = !solid
    )
}

/**
 * Draws the subtle iOS 27/28 top-lit specular glass reflection inside a clipped
 * surface with zero per-frame allocations.
 */
internal fun Modifier.liquidGlassSheen(
    enabled: Boolean,
    topColor: Color,
    bottomColor: Color = Color.Transparent,
    coverageFraction: Float = 0.34f
): Modifier = if (!enabled) this else drawWithCache {
    val sheenBrush = Brush.verticalGradient(
        colors = listOf(topColor, bottomColor),
        startY = 0f,
        endY = (size.height * coverageFraction).coerceAtLeast(1f)
    )
    onDrawBehind {
        drawRect(brush = sheenBrush)
    }
}

/**
 * Vertical specular border stroke: brighter light catch along the top lip
 * tapering to a subtle translucent hairline around the lower curve.
 */
internal fun liquidGlassBorderStroke(
    width: Dp,
    rimTop: Color,
    rimBottom: Color
): BorderStroke = BorderStroke(
    width = width,
    brush = Brush.verticalGradient(listOf(rimTop, rimBottom))
)

/**
 * Core iOS 27/28 Liquid Glass container used by floating docks, headers,
 * cards, popovers, Dynamic Island sheets, and panels across the app.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    level: BousygramGlassLevel = BousygramGlassLevel.PANEL,
    shape: Shape = RoundedCornerShape(LocalBousygramStyleSpec.current.panelCorner),
    accentTinted: Boolean = false,
    focused: Boolean = false,
    contentColor: Color = if (level == BousygramGlassLevel.DYNAMIC_ISLAND) {
        Color(0xFFF5F8FF)
    } else {
        MaterialTheme.colorScheme.onSurface
    },
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val motion = rememberBousygramMotionPolicy()
    val material = resolveGlassMaterial(
        level = level,
        colors = colors,
        halo = halo,
        reduceTransparency = reduceTransparency,
        lowRam = motion.lowRam,
        accentTinted = accentTinted
    )
    val border = if (focused) {
        BorderStroke(3.dp, colors.primary)
    } else {
        liquidGlassBorderStroke(material.borderWidth, material.rimTop, material.rimBottom)
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = material.fill,
        contentColor = contentColor,
        border = border,
        shadowElevation = material.shadowElevation,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.liquidGlassSheen(
                enabled = material.showSpecularSheen,
                topColor = material.specularTop,
                bottomColor = material.specularBottom
            ),
            content = content
        )
    }
}

/**
 * Lightweight Modifier-only variant for existing containers that manage their
 * own Surface or clickable/focus chain (such as TvFocusSurface and HUD bars).
 */
@Composable
internal fun Modifier.liquidGlassChrome(
    shape: Shape,
    level: BousygramGlassLevel = BousygramGlassLevel.FLOATING_CHROME,
    accentTinted: Boolean = false
): Modifier {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val motion = rememberBousygramMotionPolicy()
    val material = resolveGlassMaterial(
        level = level,
        colors = colors,
        halo = halo,
        reduceTransparency = reduceTransparency,
        lowRam = motion.lowRam,
        accentTinted = accentTinted
    )
    return this
        .clip(shape)
        .background(material.fill, shape)
        .liquidGlassSheen(
            enabled = material.showSpecularSheen,
            topColor = material.specularTop,
            bottomColor = material.specularBottom
        )
        .border(
            width = material.borderWidth,
            brush = Brush.verticalGradient(listOf(material.rimTop, material.rimBottom)),
            shape = shape
        )
}
