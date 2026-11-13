package com.vaydelux.bousygram.presentation.design.contract

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.vaydelux.bousygram.presentation.adaptive.AdaptiveMetrics
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.bousygramResponsiveTokens

/**
 * Portable, brand-neutral visual *roles*, backed by the active Material 3 scheme.
 *
 * This is not a second theme, color palette, user preference, or branding API.
 * Android components read MaterialTheme through this adapter. A web client may
 * implement the same roles with its existing CSS theme variables.
 */
data class StreamingVisualTokens(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val overlay: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val border: Color,
    val focus: Color,
    val action: Color,
    val onAction: Color,
    val selected: Color,
    val onSelected: Color,
    val error: Color,
    val progressTrack: Color,
    val progressFill: Color
)

fun streamingVisualTokens(scheme: ColorScheme): StreamingVisualTokens = StreamingVisualTokens(
    background = scheme.background,
    surface = scheme.surfaceContainerLow,
    raised = scheme.surfaceContainerHigh,
    overlay = scheme.surfaceContainerHighest,
    textPrimary = scheme.onSurface,
    textSecondary = scheme.onSurfaceVariant,
    textDisabled = scheme.onSurface.copy(alpha = 0.38f),
    border = scheme.outlineVariant,
    focus = scheme.primary,
    action = scheme.primary,
    onAction = scheme.onPrimary,
    selected = scheme.primaryContainer,
    onSelected = scheme.onPrimaryContainer,
    error = scheme.error,
    progressTrack = scheme.surfaceVariant,
    progressFill = scheme.primary
)

@Composable
fun rememberStreamingVisualTokens(): StreamingVisualTokens =
    streamingVisualTokens(MaterialTheme.colorScheme)

/** Layout aliases preserve existing responsive / accessible Android geometry. */
data class StreamingLayoutMetrics(
    val pagePadding: Dp,
    val gridGap: Dp,
    val cardMinWidth: Dp,
    val minControlHeight: Dp,
    val cardRadius: Dp,
    val panelRadius: Dp,
    val focusOutline: Dp,
    val contentGap: Dp
)

fun streamingLayoutMetrics(metrics: AdaptiveMetrics): StreamingLayoutMetrics {
    val existing = bousygramResponsiveTokens(metrics)
    return StreamingLayoutMetrics(
        pagePadding = existing.pagePadding,
        gridGap = existing.gridGap,
        cardMinWidth = existing.cardMinWidth,
        minControlHeight = existing.minControlHeight,
        cardRadius = BousygramUiTokens.cardCorner,
        panelRadius = BousygramUiTokens.panelCorner,
        focusOutline = BousygramUiTokens.tvFocusOutline,
        contentGap = BousygramUiTokens.contentSpacing
    )
}

/** Reject NaN, infinity and invalid durations rather than displaying fake progress. */
fun streamingProgressFraction(position: Long, duration: Long): Float? =
    if (duration <= 0L || position < 0L) null
    else (position.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0).toFloat()
