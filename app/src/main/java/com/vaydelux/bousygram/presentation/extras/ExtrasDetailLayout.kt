package com.vaydelux.bousygram.presentation.extras

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Usable-window contract, not physical pixels or CPU ABI. Keep the hero and
 * decorative art subordinate to actions on short landscape phones; retain the
 * cinematic treatment on TVs even at their common 960 x 540 dp canvas.
 */
internal data class ExtrasDetailLayout(
    val compactLandscape: Boolean,
    val showBackdrop: Boolean,
    val backdropHeight: Dp,
    val posterWidth: Dp,
    val horizontalPadding: Dp,
    val metadataInline: Boolean,
    val tightSpacing: Boolean,
    val castCardWidth: Dp,
    val relatedCardWidth: Dp,
    val reviewCollapsedLines: Int
)

internal fun resolveExtrasDetailLayout(
    width: Dp,
    height: Dp,
    isTv: Boolean,
    fontScale: Float = 1f
): ExtrasDetailLayout {
    val scale = fontScale.coerceIn(1f, 2f)
    val effectiveWidth = width / scale
    val effectiveHeight = height / scale
    val compactLandscape = !isTv && width > height && effectiveHeight < 540.dp
    val narrow = effectiveWidth < 380.dp
    val shortHeight = effectiveHeight < 550.dp
    return ExtrasDetailLayout(
        compactLandscape = compactLandscape,
        showBackdrop = !compactLandscape,
        backdropHeight = when {
            isTv -> if (shortHeight) 148.dp else 198.dp
            shortHeight -> 106.dp
            narrow -> 126.dp
            effectiveWidth < 620.dp -> 148.dp
            else -> 176.dp
        },
        posterWidth = when {
            compactLandscape -> 96.dp
            isTv -> if (effectiveWidth < 920.dp) 132.dp else 164.dp
            narrow -> 86.dp
            effectiveWidth < 620.dp -> 102.dp
            else -> 150.dp
        },
        horizontalPadding = when {
            isTv -> 28.dp
            compactLandscape -> 12.dp
            narrow -> 12.dp
            effectiveWidth < 620.dp -> 16.dp
            else -> 24.dp
        },
        metadataInline = isTv || effectiveWidth >= 325.dp,
        tightSpacing = compactLandscape || shortHeight,
        castCardWidth = when {
            isTv -> 116.dp
            narrow -> 86.dp
            else -> 102.dp
        },
        relatedCardWidth = when {
            isTv -> 166.dp
            compactLandscape -> 124.dp
            narrow -> 116.dp
            else -> 136.dp
        },
        reviewCollapsedLines = if (compactLandscape) 3 else 4
    )
}
