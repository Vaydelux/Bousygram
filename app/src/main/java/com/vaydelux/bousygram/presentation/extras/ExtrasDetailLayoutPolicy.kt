package com.vaydelux.bousygram.presentation.extras

import kotlin.math.max

/**
 * Zoku-inspired information hierarchy, translated to native Compose dp.
 *
 * Android TV uses logical window dp, never panel pixels or a 4K resolution
 * shortcut. A 960 x 540dp TV must not be treated as a landscape phone.
 * This file deliberately has no Compose dependency for JVM matrix tests.
 */
internal data class ExtrasDetailLayoutSpec(
    val horizontalGutterDp: Int,
    val sectionGapDp: Int,
    val backdropHeightDp: Int,
    val posterWidthDp: Int,
    val synopsisCollapsedLines: Int,
    val controlHeightDp: Int,
    val castWidthDp: Int,
    val sideBySideProfile: Boolean,
    val episodeColumns: Int,
    val compactChrome: Boolean
)

internal object ExtrasDetailLayoutPolicy {
    const val MAX_READABLE_WIDTH_DP = 1160

    fun resolve(
        widthDp: Float,
        heightDp: Float,
        isTv: Boolean,
        fontScale: Float = 1f
    ): ExtrasDetailLayoutSpec {
        val rawWidth = widthDp.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        val rawHeight = heightDp.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        val scale = fontScale.takeIf(Float::isFinite)?.coerceIn(1f, 2f) ?: 1f
        val effectiveWidth = rawWidth / if (isTv) 1f else scale
        val effectiveHeight = rawHeight / if (isTv) 1f else scale

        val shortPhone = !isTv && effectiveHeight < 520f
        val narrowPhone = !isTv && effectiveWidth < 600f
        val compactChrome = narrowPhone || shortPhone
        val baseGutter = when {
            isTv -> 32
            effectiveWidth < 360f -> 12
            effectiveWidth < 600f -> 16
            effectiveWidth < 900f -> 24
            else -> 32
        }
        // This is outer padding, not content scaling: on large TVs, neither
        // long reviews nor episode synopses should span the entire panel.
        val gutter = max(
            baseGutter,
            ((rawWidth - MAX_READABLE_WIDTH_DP) / 2f).toInt().coerceAtLeast(0)
        )
        return ExtrasDetailLayoutSpec(
            horizontalGutterDp = gutter,
            sectionGapDp = if (compactChrome) 12 else if (isTv) 24 else 18,
            backdropHeightDp = when {
                shortPhone -> 112
                narrowPhone -> 154
                isTv && effectiveHeight < 650f -> 192
                isTv -> 236
                effectiveWidth < 900f -> 192
                else -> 224
            },
            posterWidthDp = when {
                shortPhone -> 112
                narrowPhone -> 140
                isTv && effectiveWidth < 1100f -> 160
                isTv -> 188
                effectiveWidth < 900f -> 160
                else -> 184
            },
            synopsisCollapsedLines = if (shortPhone) 3 else 4,
            controlHeightDp = if (isTv) 56 else 52,
            castWidthDp = if (narrowPhone) 100 else if (isTv) 124 else 112,
            sideBySideProfile = isTv || (effectiveWidth >= 720f && !shortPhone),
            episodeColumns = if (effectiveWidth >= 1180f) 2 else 1,
            compactChrome = compactChrome
        )
    }
}
