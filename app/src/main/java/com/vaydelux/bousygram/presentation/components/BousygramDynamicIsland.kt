package com.vaydelux.bousygram.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramAppearanceController
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramReduceTransparency
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import com.vaydelux.bousygram.presentation.theme.tabular
import kotlinx.coroutines.launch

/** Semantic indicator tone inside the Dynamic Island capsule. */
enum class DynamicIslandTone {
    ACCENT,
    ONLINE,
    SYNCING,
    WARNING,
    ERROR
}

/** Quick action pill inside an expanded Dynamic Island Live Activity sheet. */
@Immutable
data class DynamicIslandAction(
    val id: String,
    val label: String,
    val icon: BousygramIconRef,
    val emphasized: Boolean = false,
    val onClick: () -> Unit
)

/** Live state model rendered by [BousygramDynamicIsland]. */
@Immutable
data class DynamicIslandActivityState(
    val title: String,
    val subtitle: String = "",
    val badge: String? = null,
    val tone: DynamicIslandTone = DynamicIslandTone.ACCENT,
    val isBusy: Boolean = false,
    val liveHeadline: String = title,
    val liveDetail: String = subtitle,
    val statusTag: String? = null,
    val actions: List<DynamicIslandAction> = emptyList(),
    val showQuickAppearance: Boolean = true
)

/** Pure label formatter keeping compact Dynamic Island pills crisp across narrow widths. */
internal fun formatDynamicIslandCompactTitle(raw: String, maxChars: Int = 24): String {
    val clean = raw.trim().replace(Regex("\\s+"), " ")
    if (clean.length <= maxChars) return clean
    return clean.take((maxChars - 1).coerceAtLeast(1)).trimEnd() + "…"
}

@Composable
private fun dynamicIslandToneColor(tone: DynamicIslandTone): Color {
    val colors = MaterialTheme.colorScheme
    return when (tone) {
        DynamicIslandTone.ACCENT -> colors.primary
        DynamicIslandTone.ONLINE -> Color(0xFF34D399)
        DynamicIslandTone.SYNCING -> colors.secondary
        DynamicIslandTone.WARNING -> Color(0xFFFBBF24)
        DynamicIslandTone.ERROR -> Color(0xFFFF6B80)
    }
}

/**
 * iOS 27/28 Morphing Dynamic Island inspired by Zoku TV, React Orbit 19, Kakei,
 * and Bookire:
 * - Compact obsidian glass capsule in the top chrome showing live status & context.
 * - Expands smoothly on tap or TV D-pad OK into a top-centered obsidian Live
 *   Activity glass sheet with contextual actions and instant Appearance controls.
 * - Android TV D-pad contract: participates cleanly in linear horizontal top-bar
 *   navigation, never steals initial grid focus, and restores focus to its
 *   trigger button immediately upon Back dismissal.
 */
@Composable
fun BousygramDynamicIsland(
    state: DynamicIslandActivityState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    triggerFocusRequester: FocusRequester? = null,
    statusTestTag: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val motion = rememberBousygramMotionPolicy()
    val metrics = rememberAdaptiveMetrics()
    val appearance = LocalBousygramAppearanceController.current
    val scope = rememberCoroutineScope()

    val internalTriggerFocus = remember { FocusRequester() }
    val resolvedTriggerFocus = triggerFocusRequester ?: internalTriggerFocus
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    val hasExpandableContent = state.actions.isNotEmpty() ||
        state.liveDetail.isNotBlank() ||
        (state.showQuickAppearance && appearance != null)

    val dotColor = dynamicIslandToneColor(state.tone)
    val pillShape = RoundedCornerShape(BousygramUiTokens.pillCorner)
    val islandBg = if (halo.isHighContrast) {
        colors.surfaceContainerHighest
    } else {
        Color(0xFF090D16).copy(alpha = if (reduceTransparency) 0.98f else 0.92f)
    }
    val islandFg = if (halo.isHighContrast) colors.onSurface else Color(0xFFF5F8FF)
    val islandMuted = if (halo.isHighContrast) colors.onSurfaceVariant else Color(0xFF9FB0C8)

    val borderBrush = if (focused) {
        Brush.verticalGradient(listOf(colors.primary, colors.primary))
    } else if (halo.isHighContrast) {
        Brush.verticalGradient(listOf(colors.outline, colors.outline))
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (expanded) 0.34f else 0.26f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    }
    val focusScale by animateFloatAsState(
        targetValue = if (focused && motion.animateFocusScale) BousygramUiTokens.focusScale else 1f,
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "dynamicIslandScale"
    )

    val compactTitle = remember(state.title, compact) {
        formatDynamicIslandCompactTitle(state.title, if (compact) 18 else 26)
    }

    fun dismissAndRestoreFocus() {
        expanded = false
        if (metrics.isTv || metrics.keyboardNavigation) {
            scope.launch {
                withFrameNanos { }
                runCatching { resolvedTriggerFocus.requestFocus() }
            }
        }
    }

    Surface(
        modifier = modifier
            .heightIn(min = if (metrics.isTv) 48.dp else 40.dp)
            .widthIn(max = if (compact) 236.dp else 340.dp)
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            }
            .animateContentSize(animationSpec = tween(motion.duration(180)))
            .clip(pillShape)
            .border(
                width = if (focused) BousygramUiTokens.tvFocusOutline else if (halo.isHighContrast) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = pillShape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(resolvedTriggerFocus)
            .semantics {
                contentDescription = buildString {
                    append(state.title)
                    if (!state.badge.isNullOrBlank()) append(", ${state.badge}")
                    if (hasExpandableContent) append(". Open Dynamic Island")
                }
            }
            .then(
                if (hasExpandableContent) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = dotColor),
                        role = Role.Button,
                        onClick = { expanded = !expanded }
                    )
                } else Modifier
            )
            .testTag("bousygram-dynamic-island"),
        shape = pillShape,
        color = islandBg,
        contentColor = islandFg,
        shadowElevation = if (motion.lowRam || halo.isHighContrast) 0.dp else 10.dp
    ) {
        Row(
            modifier = Modifier
                .liquidGlassSheen(
                    enabled = !halo.isHighContrast && !reduceTransparency,
                    topColor = Color.White.copy(alpha = 0.11f),
                    coverageFraction = 0.45f
                )
                .padding(
                    horizontal = if (compact) 11.dp else 14.dp,
                    vertical = if (compact) 7.dp else 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
        ) {
            if (state.isBusy && motion.animateIndeterminateProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.6.dp,
                    color = dotColor
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.24f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.5.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            Text(
                text = compactTitle,
                color = islandFg,
                style = (if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge).tabular(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(if (statusTestTag != null) Modifier.testTag(statusTestTag) else Modifier)
            )

            if (!state.badge.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(12.dp)
                        .background(Color.White.copy(alpha = 0.16f))
                )
                Text(
                    text = state.badge,
                    color = dotColor,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (hasExpandableContent) {
                BousygramIcon(
                    icon = if (expanded) BousygramIcons.ChevronUp else BousygramIcons.ChevronDown,
                    contentDescription = null,
                    tint = islandMuted,
                    size = 14.dp
                )
            }
        }
    }

    if (expanded && hasExpandableContent) {
        DynamicIslandLiveSheetDialog(
            state = state,
            dotColor = dotColor,
            onDismiss = { dismissAndRestoreFocus() }
        )
    }
}

@Composable
private fun DynamicIslandLiveSheetDialog(
    state: DynamicIslandActivityState,
    dotColor: Color,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val metrics = rememberAdaptiveMetrics()
    val appearance = LocalBousygramAppearanceController.current
    val initialSheetFocus = remember { FocusRequester() }
    var appearanceExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(metrics.isTv, metrics.keyboardNavigation) {
        if (metrics.isTv || metrics.keyboardNavigation) {
            withFrameNanos { }
            runCatching { initialSheetFocus.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = if (metrics.isTv) 32.dp else 14.dp, vertical = 14.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            LiquidGlassSurface(
                level = BousygramGlassLevel.DYNAMIC_ISLAND,
                shape = RoundedCornerShape(28.dp),
                contentColor = if (halo.isHighContrast) colors.onSurface else Color(0xFFF5F8FF),
                modifier = Modifier
                    .widthIn(min = 290.dp, max = 448.dp)
                    .fillMaxWidth()
                    .testTag("bousygram-dynamic-island-sheet")
            ) {
                val fg = if (halo.isHighContrast) colors.onSurface else Color(0xFFF5F8FF)
                val muted = if (halo.isHighContrast) colors.onSurfaceVariant else Color(0xFFA2B2C9)
                val innerCardBg = if (halo.isHighContrast) {
                    colors.surfaceContainerHigh
                } else {
                    Color(0xFF131A28).copy(alpha = if (reduceTransparency) 0.96f else 0.82f)
                }
                val innerCardBorder = if (halo.isHighContrast) {
                    colors.outline
                } else {
                    Color.White.copy(alpha = 0.11f)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header row (Bookire expanded Live Activity style)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(dotColor.copy(alpha = 0.16f))
                                .border(1.dp, dotColor.copy(alpha = 0.40f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BousygramBrandMark(size = 26.dp)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = state.statusTag ?: "Live Activity",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = dotColor,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = state.liveHeadline,
                                style = MaterialTheme.typography.titleMedium,
                                color = fg,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (state.showQuickAppearance && appearance != null) {
                            IslandCircleIconButton(
                                icon = BousygramIcons.Theme,
                                contentDescription = "Toggle quick appearance controls",
                                selected = appearanceExpanded,
                                accent = dotColor,
                                onClick = { appearanceExpanded = !appearanceExpanded },
                                modifier = Modifier.testTag("dynamic-island-appearance-toggle")
                            )
                        }
                        IslandCircleIconButton(
                            icon = BousygramIcons.Close,
                            contentDescription = "Close Dynamic Island",
                            selected = false,
                            accent = dotColor,
                            onClick = onDismiss,
                            modifier = Modifier
                                .then(if (state.actions.isEmpty()) Modifier.focusRequester(initialSheetFocus) else Modifier)
                                .testTag("dynamic-island-close")
                        )
                    }

                    // Live Context Card
                    if (state.liveDetail.isNotBlank() || !state.badge.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(innerCardBg)
                                .border(1.dp, innerCardBorder, RoundedCornerShape(18.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = state.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = fg,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!state.badge.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(BousygramUiTokens.pillCorner),
                                        color = dotColor.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.42f))
                                    ) {
                                        Text(
                                            text = state.badge,
                                            color = dotColor,
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            if (state.liveDetail.isNotBlank()) {
                                Text(
                                    text = state.liveDetail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Contextual Quick Actions (2-column sleek glass pills)
                    if (state.actions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.actions.chunked(2).forEachIndexed { rowIdx, rowActions ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowActions.forEachIndexed { colIdx, action ->
                                        IslandActionPill(
                                            action = action,
                                            accent = dotColor,
                                            isTv = metrics.isTv,
                                            onClick = {
                                                onDismiss()
                                                action.onClick()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (rowIdx == 0 && colIdx == 0) {
                                                        Modifier.focusRequester(initialSheetFocus)
                                                    } else Modifier
                                                )
                                                .testTag("dynamic-island-action-${action.id}")
                                        )
                                    }
                                    if (rowActions.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Inline iOS 27 Appearance Controls (toggled via palette button or shown when no actions)
                    if (state.showQuickAppearance && appearance != null &&
                        (appearanceExpanded || state.actions.isEmpty())
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.10f))
                        )
                        BousygramIosAppearanceControls(
                            activePreset = appearance.themePreset,
                            reduceTransparency = appearance.reduceTransparency,
                            onSelectPreset = appearance.onThemePresetChange,
                            onReduceTransparencyChange = appearance.onReduceTransparencyChange,
                            obsidianSurface = !halo.isHighContrast
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandActionPill(
    action: DynamicIslandAction,
    accent: Color,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val motion = rememberBousygramMotionPolicy()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(BousygramUiTokens.pillCorner)
    val bg by animateColorAsState(
        targetValue = when {
            action.emphasized && focused -> colors.primary
            action.emphasized -> accent.copy(alpha = 0.90f)
            focused -> Color.White.copy(alpha = 0.18f)
            else -> Color(0xFF172032)
        },
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "islandActionBg"
    )
    val fg = if (action.emphasized) Color(0xFF06131A) else Color(0xFFF3F7FF)
    val border = when {
        focused -> colors.primary
        action.emphasized -> Color.White.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.14f)
    }

    Row(
        modifier = modifier
            .heightIn(min = if (isTv) 48.dp else 42.dp)
            .clip(shape)
            .background(bg)
            .border(if (focused) BousygramUiTokens.tvFocusOutline else 1.dp, border, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)
    ) {
        BousygramIcon(
            icon = action.icon,
            contentDescription = null,
            tint = fg,
            size = 16.dp
        )
        Text(
            text = action.label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IslandCircleIconButton(
    icon: BousygramIconRef,
    contentDescription: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> accent.copy(alpha = 0.24f)
        focused -> Color.White.copy(alpha = 0.20f)
        else -> Color.White.copy(alpha = 0.09f)
    }
    val border = when {
        focused -> colors.primary
        selected -> accent.copy(alpha = 0.72f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .border(if (focused) 2.5.dp else 1.dp, border, CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BousygramIcon(
            icon = icon,
            contentDescription = null,
            tint = if (selected) accent else Color(0xFFF5F8FF),
            size = 17.dp
        )
    }
}

/**
 * Strictly non-focusable Dynamic Island capsule for Video Player loading,
 * buffering, and reconnecting states. Preserves Android TV D-pad ownership
 * and compact 320dp/540dp viewport safety.
 */
@Composable
fun BousygramPlayerDynamicIsland(
    title: String,
    detail: String,
    isBusy: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    tone: DynamicIslandTone = DynamicIslandTone.ACCENT
) {
    val halo = LocalBousygramHaloColors.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val motion = rememberBousygramMotionPolicy()
    val colors = MaterialTheme.colorScheme
    val accent = dynamicIslandToneColor(tone)
    val pillShape = RoundedCornerShape(BousygramUiTokens.pillCorner)
    val bg = if (halo.isHighContrast) {
        colors.surfaceContainerHighest
    } else {
        Color(0xFF080C15).copy(alpha = if (reduceTransparency) 0.98f else 0.92f)
    }
    val fg = if (halo.isHighContrast) colors.onSurface else Color(0xFFF5F8FF)
    val muted = if (halo.isHighContrast) colors.onSurfaceVariant else Color(0xFFA7B7CE)

    Surface(
        modifier = modifier
            .widthIn(max = if (compact) 296.dp else 390.dp)
            .clip(pillShape)
            .border(
                width = if (halo.isHighContrast) 1.5.dp else 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        if (halo.isHighContrast) colors.outline else Color.White.copy(alpha = 0.28f),
                        if (halo.isHighContrast) colors.outline else Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = pillShape
            )
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = if (detail.isNotBlank()) "$title. $detail" else title
            },
        shape = pillShape,
        color = bg,
        contentColor = fg,
        shadowElevation = if (motion.lowRam || halo.isHighContrast) 0.dp else 10.dp
    ) {
        Row(
            modifier = Modifier
                .liquidGlassSheen(
                    enabled = !halo.isHighContrast && !reduceTransparency,
                    topColor = Color.White.copy(alpha = 0.11f),
                    coverageFraction = 0.45f
                )
                .padding(
                    horizontal = if (compact) 13.dp else 16.dp,
                    vertical = if (compact) 8.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isBusy && motion.animateIndeterminateProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                    color = accent,
                    strokeWidth = 2.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    color = fg,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        color = muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
