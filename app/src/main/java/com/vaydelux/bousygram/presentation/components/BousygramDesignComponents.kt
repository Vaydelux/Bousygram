package com.vaydelux.bousygram.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaydelux.bousygram.presentation.theme.BousygramDesignStyle
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramDisplaySpec
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import com.vaydelux.bousygram.presentation.theme.tabular

/**
 * One line-height contract for navigation tabs and action buttons across
 * Telegram Home/Library/Search/Favorites and Extras. Font scaling remains
 * enabled; the baseline and vertical hit target are consistent on all routes.
 */
@Composable
internal fun bousygramControlLabelStyle(isTv: Boolean) =
    (if (isTv) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge)
        .copy(lineHeight = if (isTv) 22.sp else 20.sp)

/** Shared information hierarchy for Telegram Library, Favorites and Extras. */
@Composable
fun BousygramPageHeading(
    eyebrow: String,
    title: String,
    subtitle: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    icon: BousygramIconRef? = null
) {
    val halo = LocalBousygramHaloColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .width(24.dp)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(listOf(halo.haloStart, halo.haloEnd)),
                        RoundedCornerShape(3.dp)
                    )
            )
            if (icon != null) {
                BousygramIcon(
                    icon = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 14.dp
                )
            }
            Text(
                eyebrow.uppercase(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            style = if (compact) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Unified content-first title for short portrait/landscape routes and results. */
@Composable
fun BousygramCompactPageTitle(
    title: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(lineHeight = 28.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (status.isNotBlank()) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Opaque status token that remains readable in dark, light and high-contrast. */
@Composable
fun BousygramStatusPill(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    gold: Boolean = false,
    icon: BousygramIconRef? = null
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val containerColor = when {
        gold -> halo.goldContainer
        accent -> colors.primaryContainer
        else -> colors.surfaceContainerHigh
    }
    val contentColor = when {
        gold -> halo.onGoldContainer
        accent -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val borderColor = when {
        gold -> halo.goldAccent.copy(alpha = 0.45f)
        accent -> colors.primary.copy(alpha = 0.42f)
        else -> colors.outlineVariant.copy(alpha = 0.65f)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(BousygramUiTokens.pillCorner),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                BousygramIcon(
                    icon = icon,
                    contentDescription = null,
                    tint = contentColor,
                    size = 13.dp
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = contentColor,
                fontWeight = if (accent || gold) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Shared navigation tab pill for Telegram, Extras, phone, tablet, and Android TV.
 * Supports Lucide icons, optional numeric count badges, icon-only compact mode
 * (eliminating text cut-offs on narrow displays), and automatic tooltips on
 * focus dwell, pointer hover, and touch selection.
 */
@Composable
fun BousygramSectionTab(
    label: String,
    active: Boolean,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    accessibilityLabel: String = label,
    icon: BousygramIconRef? = null,
    badgeCount: Int? = null,
    iconOnly: Boolean = false,
    tooltip: String? = accessibilityLabel,
    tooltipDetail: String? = null,
    tooltipPlacement: BousygramTooltipPlacement = BousygramTooltipPlacement.AUTO_BELOW
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val spec = LocalBousygramDisplaySpec.current
    val styleSpec = LocalBousygramStyleSpec.current
    val motion = rememberBousygramMotionPolicy()
    val tooltipState = rememberBousygramTooltipState()
    var focused by remember { mutableStateOf(false) }

    val highlight by animateColorAsState(
        targetValue = when {
            active && styleSpec.segmentedTabs && !halo.isHighContrast -> colors.surface
            active -> colors.primaryContainer
            focused -> colors.surfaceContainerHighest
            styleSpec.useTonalSurfaces -> colors.surfaceContainerHigh
            else -> colors.surfaceContainerLow
        },
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "sectionTabSurface"
    )
    val outline by animateColorAsState(
        targetValue = when {
            focused -> colors.primary
            active && styleSpec.segmentedTabs && !halo.isHighContrast -> colors.outlineVariant.copy(alpha = 0.55f)
            active -> halo.haloStart.copy(alpha = if (halo.isHighContrast) 1f else 0.72f)
            styleSpec.style == BousygramDesignStyle.COZY_KAWAII -> colors.primary.copy(alpha = 0.28f)
            else -> colors.outlineVariant.copy(alpha = 0.70f)
        },
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "sectionTabOutline"
    )
    val contentColor = when {
        active && styleSpec.segmentedTabs && !halo.isHighContrast -> colors.onSurface
        active -> colors.onPrimaryContainer
        focused -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val showLabel = !iconOnly || icon == null
    val effectiveTooltipDetail = tooltipDetail ?: badgeCount?.let { "$it" }

    val tabBorderWidth = when {
        focused -> BousygramUiTokens.tvFocusOutline
        active -> if (styleSpec.useTonalSurfaces && !halo.isHighContrast) 0.dp else 1.5.dp
        halo.isHighContrast -> 1.dp
        else -> styleSpec.cardBorderWidth
    }

    OutlinedButton(
        onClick = {
            if (!isTv && iconOnly) {
                tooltipState.flashSelection()
            }
            onClick()
        },
        modifier = modifier
            .defaultMinSize(
                minWidth = if (iconOnly) {
                    if (isTv) BousygramUiTokens.tvControlHeight else BousygramUiTokens.compactControlHeight
                } else 1.dp,
                minHeight = if (isTv) BousygramUiTokens.tvControlHeight
                    else BousygramUiTokens.compactControlHeight
            )
            .heightIn(
                min = if (isTv) BousygramUiTokens.tvControlHeight
                    else BousygramUiTokens.compactControlHeight
            )
            .semantics {
                selected = active
                contentDescription = if (badgeCount != null) "$accessibilityLabel, $badgeCount"
                    else accessibilityLabel
            }
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(styleSpec.tabCorner),
        border = if (tabBorderWidth > 0.dp) BorderStroke(width = tabBorderWidth, color = outline) else null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = highlight,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(
            horizontal = when {
                iconOnly -> 12.dp
                isTv -> 18.dp
                compact -> 11.dp
                else -> 14.dp
            },
            vertical = 8.dp
        )
    ) {
        BousygramTooltipBox(
            tooltip = tooltip,
            detail = effectiveTooltipDetail,
            icon = icon,
            focused = focused,
            placement = tooltipPlacement,
            dwellMs = if (iconOnly) 0L else BousygramUiTokens.tooltipDwellMs,
            state = tooltipState
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 8.dp else 6.dp)
            ) {
                if (icon != null) {
                    BousygramIcon(
                        icon = icon,
                        contentDescription = null,
                        tint = when {
                            active && styleSpec.segmentedTabs && !halo.isHighContrast -> colors.primary
                            active -> colors.onPrimaryContainer
                            focused -> colors.primary
                            else -> colors.onSurfaceVariant
                        },
                        size = if (isTv) spec.iconSizeAction else 18.dp
                    )
                }
                if (showLabel) {
                    Text(
                        text = label,
                        style = bousygramControlLabelStyle(isTv),
                        fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showLabel && badgeCount != null && badgeCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(BousygramUiTokens.pillCorner),
                        color = if (active) colors.primary.copy(alpha = 0.22f)
                            else colors.surfaceContainerHighest
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = if (active) contentColor else colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unified Cinema Halo icon button with built-in multi-input tooltip, guaranteed
 * 48dp touch / 52-56dp TV focus targets, and high-visibility D-pad focus ring.
 */
@Composable
fun BousygramIconButton(
    icon: BousygramIconRef,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltip: String = contentDescription,
    tooltipDetail: String? = null,
    tooltipPlacement: BousygramTooltipPlacement = BousygramTooltipPlacement.AUTO_BELOW,
    selected: Boolean = false,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    circular: Boolean = false,
    minSize: Dp? = null,
    iconSize: Dp? = null
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val spec = LocalBousygramDisplaySpec.current
    val styleSpec = LocalBousygramStyleSpec.current
    val motion = rememberBousygramMotionPolicy()
    val tooltipState = rememberBousygramTooltipState()
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    val targetSize = minSize ?: if (spec.isTv) spec.minControlHeight else BousygramUiTokens.touchTarget
    val resolvedIconSize = iconSize ?: spec.iconSizeAction
    val shape = if (circular) RoundedCornerShape(BousygramUiTokens.pillCorner)
        else RoundedCornerShape(styleSpec.tabCorner)

    val scale by animateFloatAsState(
        targetValue = if (focused && motion.animateFocusScale) BousygramUiTokens.focusScale else 1f,
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "iconBtnScale"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.surfaceContainerLow.copy(alpha = 0.45f)
            emphasized -> colors.primary
            selected -> colors.primaryContainer
            focused -> colors.surfaceContainerHighest
            styleSpec.useTonalSurfaces -> colors.surfaceContainerHigh
            styleSpec.style == BousygramDesignStyle.IPHONE_GLASS && !halo.isHighContrast -> colors.surface.copy(alpha = 0.90f)
            else -> colors.surfaceContainerLow
        },
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "iconBtnBg"
    )
    val tintColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        emphasized -> colors.onPrimary
        selected -> colors.onPrimaryContainer
        focused -> colors.primary
        else -> colors.onSurface
    }
    val borderColor = when {
        focused -> colors.primary
        selected -> colors.primary.copy(alpha = 0.72f)
        emphasized -> Color.Transparent
        styleSpec.style == BousygramDesignStyle.COZY_KAWAII -> colors.primary.copy(alpha = 0.30f)
        else -> colors.outlineVariant.copy(alpha = 0.72f)
    }
    val borderWidth = when {
        focused -> BousygramUiTokens.tvFocusOutline
        selected -> 1.5.dp
        halo.isHighContrast -> 1.dp
        else -> styleSpec.cardBorderWidth
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = targetSize, minHeight = targetSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgColor)
            .then(
                if (borderWidth > 0.dp) Modifier.border(width = borderWidth, color = borderColor, shape = shape)
                else Modifier
            )
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.primary),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    if (!spec.isTv) tooltipState.flashSelection()
                    onClick()
                }
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        BousygramTooltipBox(
            tooltip = tooltip,
            detail = tooltipDetail,
            icon = icon,
            focused = focused,
            placement = tooltipPlacement,
            dwellMs = 0L,
            state = tooltipState,
            enabled = enabled
        ) {
            BousygramIcon(
                icon = icon,
                contentDescription = null,
                tint = tintColor,
                size = resolvedIconSize
            )
        }
    }
}
