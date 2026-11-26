package com.vaydelux.bousygram.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.theme.BousygramIosAccentTint
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramAppearanceController
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy

/**
 * Curated iOS 27/28 Appearance control surface inspired by the React Orbit 19
 * appearance popover:
 * - Segmented Dark / Light / Contrast mode switcher
 * - 2-column iOS Accent Tint glass swatches
 * - iOS 27 Reduce Transparency solid-surface accessibility toggle
 */
@Composable
fun BousygramIosAppearanceControls(
    activePreset: BousygramThemePreset,
    reduceTransparency: Boolean,
    onSelectPreset: (BousygramThemePreset) -> Unit,
    onReduceTransparencyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    darkModeFocusRequester: FocusRequester? = null,
    onOpenFullStudio: (() -> Unit)? = null,
    obsidianSurface: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val motion = rememberBousygramMotionPolicy()
    val isTv = rememberAdaptiveMetrics().isTv
    val activeTint = remember(activePreset) { BousygramIosAccentTint.fromPreset(activePreset) }
    val isHighContrast = activePreset == BousygramThemePreset.HIGH_CONTRAST
    val isLight = activePreset.isLightPreset

    val labelColor = if (obsidianSurface) Color(0xFFB3C0D6) else colors.onSurfaceVariant
    val primaryText = if (obsidianSurface) Color(0xFFF5F8FF) else colors.onSurface
    val trayFill = if (obsidianSurface) {
        Color(0xFF141B29).copy(alpha = if (reduceTransparency) 1f else 0.86f)
    } else {
        colors.surfaceContainerHigh.copy(alpha = if (reduceTransparency || halo.isHighContrast) 1f else 0.72f)
    }
    val trayBorder = if (obsidianSurface) {
        Color.White.copy(alpha = 0.12f)
    } else if (halo.isLight) {
        Color(0xFF0F172A).copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    Column(
        modifier = modifier.fillMaxWidth().testTag("ios-appearance-controls"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Appearance Mode Segmented Bar (Dark | Light | Contrast)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                fontWeight = FontWeight.SemiBold
            )
            val trayShape = RoundedCornerShape(BousygramUiTokens.pillCorner)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(trayShape)
                    .background(trayFill)
                    .border(1.dp, trayBorder, trayShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IosModeSegment(
                    label = "Dark",
                    icon = BousygramIcons.SparklesMoonFallback,
                    selected = !isLight && !isHighContrast,
                    isTv = isTv,
                    obsidianSurface = obsidianSurface,
                    onClick = { onSelectPreset(activeTint.presetFor(lightMode = false)) },
                    modifier = Modifier
                        .weight(1f)
                        .then(if (darkModeFocusRequester != null) Modifier.focusRequester(darkModeFocusRequester) else Modifier)
                        .testTag("ios-appearance-dark")
                )
                IosModeSegment(
                    label = "Light",
                    icon = BousygramIcons.Brightness,
                    selected = isLight && !isHighContrast,
                    isTv = isTv,
                    obsidianSurface = obsidianSurface,
                    onClick = { onSelectPreset(activeTint.presetFor(lightMode = true)) },
                    modifier = Modifier.weight(1f).testTag("ios-appearance-light")
                )
                IosModeSegment(
                    label = "Contrast",
                    icon = BousygramIcons.Shield,
                    selected = isHighContrast,
                    isTv = isTv,
                    obsidianSurface = obsidianSurface,
                    onClick = { onSelectPreset(BousygramThemePreset.HIGH_CONTRAST) },
                    modifier = Modifier.weight(1f).testTag("ios-appearance-contrast")
                )
            }
        }

        // 2. iOS Accent Tint 2-column Glass Swatches
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "iOS Accent Tint",
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                fontWeight = FontWeight.SemiBold
            )
            BousygramIosAccentTint.entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { tint ->
                        val isSelected = !isHighContrast && activeTint == tint
                        IosAccentSwatchButton(
                            tint = tint,
                            selected = isSelected,
                            isTv = isTv,
                            obsidianSurface = obsidianSurface,
                            reduceTransparency = reduceTransparency,
                            onClick = { onSelectPreset(tint.presetFor(lightMode = isLight)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ios-accent-${tint.id}")
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // 3. Reduce Transparency Switch (iOS 27 high-legibility solid surfaces)
        var switchFocused by remember { mutableStateOf(false) }
        val rowShape = RoundedCornerShape(16.dp)
        val rowBorder by animateColorAsState(
            targetValue = if (switchFocused) colors.primary else trayBorder,
            animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
            label = "reduceTransparencyBorder"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .background(trayFill)
                .border(if (switchFocused) BousygramUiTokens.tvFocusOutline else 1.dp, rowBorder, rowShape)
                .onFocusChanged { switchFocused = it.isFocused }
                .heightIn(min = 48.dp)
                .toggleable(value = reduceTransparency, role = Role.Switch, onValueChange = onReduceTransparencyChange)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("ios-reduce-transparency-toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                BousygramIcon(
                    icon = BousygramIcons.Layers,
                    contentDescription = null,
                    tint = colors.primary,
                    size = 17.dp
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Reduce Transparency",
                    style = MaterialTheme.typography.labelLarge,
                    color = primaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "iOS 27 high-legibility solid surfaces",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = reduceTransparency,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
                    uncheckedThumbColor = if (obsidianSurface) Color(0xFFD5DFEE) else colors.onSurfaceVariant,
                    uncheckedTrackColor = if (obsidianSurface) Color(0xFF253044) else colors.surfaceContainerHighest
                )
            )
        }

        if (onOpenFullStudio != null) {
            BousygramButton(
                text = "All 16 themes & styles",
                leadingIcon = BousygramIcons.Theme,
                onClick = onOpenFullStudio,
                modifier = Modifier.fillMaxWidth().testTag("ios-appearance-open-studio")
            )
        }
    }
}

@Composable
private fun IosModeSegment(
    label: String,
    icon: BousygramIconRef,
    selected: Boolean,
    isTv: Boolean,
    obsidianSurface: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val motion = rememberBousygramMotionPolicy()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(BousygramUiTokens.pillCorner)
    val bg by animateColorAsState(
        targetValue = when {
            selected -> colors.primary
            focused -> if (obsidianSurface) Color.White.copy(alpha = 0.14f) else colors.surfaceContainerHighest
            else -> Color.Transparent
        },
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "iosModeSegBg"
    )
    val fg = when {
        selected -> colors.onPrimary
        obsidianSurface -> Color(0xFFE6EEF9)
        focused -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (focused) Modifier.border(2.5.dp, if (selected) colors.onPrimary else colors.primary, shape)
                else Modifier
            )
            .semantics {
                this.selected = selected
                this.contentDescription = "$label appearance"
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = fg),
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
    ) {
        BousygramIcon(
            icon = icon,
            contentDescription = null,
            tint = fg,
            size = 15.dp
        )
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IosAccentSwatchButton(
    tint: BousygramIosAccentTint,
    selected: Boolean,
    isTv: Boolean,
    obsidianSurface: Boolean,
    reduceTransparency: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val motion = rememberBousygramMotionPolicy()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    val bg by animateColorAsState(
        targetValue = when {
            selected && obsidianSurface -> tint.swatchColor.copy(alpha = if (reduceTransparency) 0.28f else 0.20f)
            selected -> colors.primaryContainer
            focused && obsidianSurface -> Color.White.copy(alpha = 0.14f)
            focused -> colors.surfaceContainerHighest
            obsidianSurface -> Color(0xFF141B29).copy(alpha = if (reduceTransparency) 1f else 0.76f)
            else -> colors.surfaceContainerLow.copy(alpha = if (reduceTransparency || halo.isHighContrast) 1f else 0.80f)
        },
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "iosAccentBg"
    )
    val borderColor = when {
        focused -> colors.primary
        selected -> tint.swatchColor.copy(alpha = 0.88f)
        obsidianSurface -> Color.White.copy(alpha = 0.12f)
        halo.isLight -> Color(0xFF0F172A).copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val textColor = when {
        selected && !obsidianSurface -> colors.onPrimaryContainer
        obsidianSurface -> Color(0xFFF5F8FF)
        else -> colors.onSurface
    }

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg)
            .border(
                width = if (focused) BousygramUiTokens.tvFocusOutline else if (selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .semantics {
                this.selected = selected
                this.contentDescription = "${tint.displayName} accent tint"
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(tint.swatchColor)
                .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
        )
        Text(
            text = tint.displayName,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Floating iOS 27 Appearance Popover anchored near the top-end of the screen,
 * matching the React Orbit 19 popover aesthetic with clean TV D-pad focus
 * containment and Back focus restoration.
 */
@Composable
fun BousygramAppearancePopoverDialog(
    onDismiss: () -> Unit,
    onOpenFullStudio: (() -> Unit)? = null
) {
    val controller = LocalBousygramAppearanceController.current ?: return
    val metrics = rememberAdaptiveMetrics()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(metrics.isTv) {
        if (metrics.isTv) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (metrics.isTv) 32.dp else 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            LiquidGlassSurface(
                level = BousygramGlassLevel.POPOVER,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 380.dp)
                    .fillMaxWidth()
                    .testTag("ios-appearance-popover")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Appearance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "iOS 27 Liquid Glass",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BousygramIconButton(
                            icon = BousygramIcons.Close,
                            contentDescription = "Close appearance popover",
                            tooltip = "Close",
                            circular = true,
                            minSize = 40.dp,
                            iconSize = 16.dp,
                            onClick = onDismiss
                        )
                    }
                    BousygramIosAppearanceControls(
                        activePreset = controller.themePreset,
                        reduceTransparency = controller.reduceTransparency,
                        onSelectPreset = controller.onThemePresetChange,
                        onReduceTransparencyChange = controller.onReduceTransparencyChange,
                        darkModeFocusRequester = firstFocus,
                        onOpenFullStudio = onOpenFullStudio?.let { open ->
                            {
                                onDismiss()
                                open()
                            }
                        }
                    )
                }
            }
        }
    }
}

private val BousygramIcons.SparklesMoonFallback: BousygramIconRef
    get() = BousygramIcons.Wand
