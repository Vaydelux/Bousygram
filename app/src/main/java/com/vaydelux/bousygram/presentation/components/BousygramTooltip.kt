package com.vaydelux.bousygram.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramDisplaySpec
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import kotlinx.coroutines.delay

enum class BousygramTooltipPlacement {
    AUTO_BELOW,
    AUTO_ABOVE,
    AUTO_END
}

/**
 * Controller allowing callers (tabs, icon buttons, player controls) to trigger
 * brief touch-selection confirmation tooltips in addition to automatic focus dwell
 * and pointer hover.
 */
class BousygramTooltipState {
    internal var selectionTick by mutableIntStateOf(0)
        private set

    fun flashSelection() {
        selectionTick += 1
    }
}

@Composable
fun rememberBousygramTooltipState(): BousygramTooltipState = remember { BousygramTooltipState() }

/**
 * Multi-input branded tooltip safe for Android TV D-pad, mouse/trackpad hover,
 * and touch tab/icon confirmation.
 *
 * - Never steals or traps D-pad focus (`PopupProperties(focusable = false)`).
 * - Automatically flips and clamps inside window + TV overscan safe bounds.
 * - Respects [rememberBousygramMotionPolicy] (instant on low-RAM / reduced motion).
 */
@Composable
fun BousygramTooltipBox(
    tooltip: String?,
    focused: Boolean,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: BousygramIconRef? = null,
    placement: BousygramTooltipPlacement = BousygramTooltipPlacement.AUTO_BELOW,
    dwellMs: Long = BousygramUiTokens.tooltipDwellMs,
    state: BousygramTooltipState = rememberBousygramTooltipState(),
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    if (!enabled || tooltip.isNullOrBlank()) {
        Box(modifier = modifier, content = content)
        return
    }

    val motion = rememberBousygramMotionPolicy()
    val halo = LocalBousygramHaloColors.current
    val spec = LocalBousygramDisplaySpec.current
    val styleSpec = LocalBousygramStyleSpec.current
    val density = LocalDensity.current

    var hovered by remember { mutableStateOf(false) }
    var focusRevealed by remember { mutableStateOf(false) }
    var tapRevealed by remember { mutableStateOf(false) }

    LaunchedEffect(focused, dwellMs) {
        if (focused) {
            if (dwellMs > 0L) delay(dwellMs)
            focusRevealed = true
        } else {
            focusRevealed = false
        }
    }

    LaunchedEffect(state.selectionTick) {
        if (state.selectionTick > 0) {
            tapRevealed = true
            delay(BousygramUiTokens.tooltipTapDurationMs)
            tapRevealed = false
        }
    }

    val visible = focusRevealed || hovered || tapRevealed
    val gapPx = with(density) {
        if (spec.isTv) 14.dp.roundToPx() else 12.dp.roundToPx()
    }
    val safeMarginPx = with(density) {
        if (spec.isTv) 16.dp.roundToPx() else 8.dp.roundToPx()
    }
    val positionProvider = remember(placement, gapPx, safeMarginPx) {
        BousygramTooltipPositionProvider(
            placement = placement,
            gapPx = gapPx,
            safeMarginPx = safeMarginPx
        )
    }

    Box(
        modifier = modifier.pointerInput(tooltip) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Enter -> hovered = true
                        PointerEventType.Exit -> hovered = false
                        else -> Unit
                    }
                }
            }
        }
    ) {
        content()
        if (visible) {
            Popup(
                popupPositionProvider = positionProvider,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    clippingEnabled = true
                )
            ) {
                val fadeMs = motion.duration(BousygramUiTokens.tooltipFadeMs)
                var entered by remember { mutableStateOf(fadeMs == 0) }
                LaunchedEffect(Unit) { entered = true }
                AnimatedVisibility(
                    visible = entered,
                    enter = fadeIn(tween(fadeMs)),
                    exit = fadeOut(tween(fadeMs))
                ) {
                    val tooltipIconTint = if (halo.isLight) BousygramUiTokens.mascotCyan else halo.haloStart
                    Surface(
                        shape = RoundedCornerShape(styleSpec.tooltipCorner),
                        color = halo.tooltipFill,
                        contentColor = halo.tooltipContent,
                        border = BorderStroke(
                            width = if (halo.isHighContrast) 2.dp else 1.dp,
                            color = halo.tooltipBorder
                        ),
                        shadowElevation = if (motion.lowRam) 0.dp else 8.dp,
                        modifier = Modifier
                            .widthIn(max = spec.tooltipMaxWidth)
                            .clearAndSetSemantics { testTag = "bousygram-tooltip-popup" }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (icon != null) {
                                BousygramIcon(
                                    icon = icon,
                                    contentDescription = null,
                                    tint = tooltipIconTint,
                                    size = 14.dp
                                )
                            }
                            Text(
                                text = tooltip,
                                color = halo.tooltipContent,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!detail.isNullOrBlank()) {
                                Text(
                                    text = "· $detail",
                                    color = halo.tooltipContent.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class BousygramTooltipPositionProvider(
    private val placement: BousygramTooltipPlacement,
    private val gapPx: Int,
    private val safeMarginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - safeMarginPx).coerceAtLeast(safeMarginPx)
        val maxY = (windowSize.height - popupContentSize.height - safeMarginPx).coerceAtLeast(safeMarginPx)

        val centeredX = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(safeMarginPx, maxX)
        val centeredY = (anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2)
            .coerceIn(safeMarginPx, maxY)

        val aboveY = anchorBounds.top - popupContentSize.height - gapPx
        val belowY = anchorBounds.bottom + gapPx
        val endX = anchorBounds.right + gapPx
        val startX = anchorBounds.left - popupContentSize.width - gapPx

        return when (placement) {
            BousygramTooltipPlacement.AUTO_ABOVE -> {
                val y = if (aboveY >= safeMarginPx) {
                    aboveY.coerceIn(safeMarginPx, maxY)
                } else {
                    belowY.coerceIn(safeMarginPx, maxY)
                }
                IntOffset(centeredX, y)
            }
            BousygramTooltipPlacement.AUTO_BELOW -> {
                val y = if (belowY + popupContentSize.height <= windowSize.height - safeMarginPx) {
                    belowY.coerceIn(safeMarginPx, maxY)
                } else {
                    aboveY.coerceIn(safeMarginPx, maxY)
                }
                IntOffset(centeredX, y)
            }
            BousygramTooltipPlacement.AUTO_END -> {
                val x = if (endX + popupContentSize.width <= windowSize.width - safeMarginPx) {
                    endX.coerceIn(safeMarginPx, maxX)
                } else {
                    startX.coerceIn(safeMarginPx, maxX)
                }
                IntOffset(x, centeredY)
            }
        }
    }
}
