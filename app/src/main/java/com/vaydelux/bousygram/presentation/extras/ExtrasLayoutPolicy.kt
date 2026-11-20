package com.vaydelux.bousygram.presentation.extras

/**
 * One viewport contract for the Extras catalog. Values are in dp rather than
 * physical pixels so Android TV (including 4K), phones and tablets share a
 * predictable spacing rhythm regardless of density.
 *
 * Keep this policy independent of Compose for fast JVM regression tests.
 */
internal data class ExtrasLayoutSpec(
    val horizontalGutterDp: Int,
    val searchFilterGapDp: Int,
    val filterResultsGapDp: Int,
    val filterChipGapDp: Int,
    val gridCardMinWidthDp: Int,
    val gridGapDp: Int,
    val minControlHeightDp: Int,
    val compactHeader: Boolean,
    val landscapeToolbar: Boolean
)

internal object ExtrasLayoutPolicy {
    fun resolve(
        widthDp: Float,
        heightDp: Float,
        isTv: Boolean,
        fontScale: Float = 1f
    ): ExtrasLayoutSpec {
        val scale = fontScale.takeIf(Float::isFinite)?.coerceIn(1f, 2f) ?: 1f
        // Use the *actual* available window dimensions for orientation detection.
        // Dividing dimensions by font scale can make a landscape phone appear
        // portrait and can force unnecessary one-column poster grids.
        val rawWidth = widthDp.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        val rawHeight = heightDp.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        // Font scale changes typography, not available layout width. Keep
        // grid sizing in real dp; use scale only to choose roomier cards.
        val width = rawWidth
        val isLandscape = rawWidth > rawHeight * 1.15f
        val compactHeader = !isTv && (rawHeight < 520f || (isLandscape && rawHeight < 600f))
        // At 200% text scale a 600dp row cannot accommodate three
        // categories plus navigation. Fall back to stacked tabs.
        val landscapeToolbar = !isTv && isLandscape &&
            rawWidth >= (if (scale >= 1.3f) 900f else 760f) &&
            rawHeight < 600f
        val narrow = width < 600f
        return ExtrasLayoutSpec(
            horizontalGutterDp = when {
                isTv -> 32
                landscapeToolbar -> 16
                width < 360f -> 12
                narrow -> 16
                width < 900f -> 24
                else -> 32
            },
            searchFilterGapDp = if (compactHeader) 8 else 16,
            filterResultsGapDp = if (landscapeToolbar) 6 else if (compactHeader) 10 else 18,
            filterChipGapDp = if (narrow) 8 else 12,
            gridCardMinWidthDp = when {
                isTv -> if (width < 900f) 152 else 176
                landscapeToolbar -> if (scale >= 1.3f) 160 else if (rawHeight < 390f) 112 else 132
                scale >= 1.45f && width < 430f -> 176
                width < 360f -> 128
                width < 600f -> 144
                width < 900f -> 160
                else -> 178
            },
            gridGapDp = if (landscapeToolbar) 8
                else if (width < 600f) 10 else if (isTv) 16 else 14,
            minControlHeightDp = if (isTv) 56 else 48,
            compactHeader = compactHeader,
            landscapeToolbar = landscapeToolbar
        )
    }

    /**
     * A short Android TV canvas is not a landscape phone. Keep the TV detail
     * hero, readable poster and remote-target spacing even at 960×540dp.
     * Only phones/tablets use the compressed short-viewport detail treatment.
     */
    fun compactDetailViewport(
        isTv: Boolean,
        isShortWindow: Boolean,
        effectiveHeightDp: Float
    ): Boolean = !isTv && (isShortWindow ||
        (effectiveHeightDp.isFinite() && effectiveHeightDp < 600f))
}
