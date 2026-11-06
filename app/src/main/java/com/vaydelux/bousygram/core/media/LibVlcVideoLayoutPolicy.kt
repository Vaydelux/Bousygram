package com.vaydelux.bousygram.core.media

import kotlin.math.max

/** Exactly one native layout for each distinct video/surface geometry. */
internal enum class LibVlcResizeMode { FIT, ZOOM, STRETCH }

internal data class LibVlcVideoLayout(
    val aspectRatio: String?,
    val scale: Float
)

/**
 * Fit: native autoscale, preserve original aspect ratio.
 * Zoom: scale the visible image until the entire viewport is covered; crop edges.
 * Stretch: force the viewport's aspect ratio; never crop, but distort geometry.
 *
 * No guessed video dimensions. Defer Zoom/Stretch until a valid surface and
 * the decoded video track are known, avoiding infinite/native-invalid scales.
 */
internal fun libVlcVideoLayout(
    mode: LibVlcResizeMode,
    windowWidth: Int,
    windowHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
    sarNumerator: Int = 1,
    sarDenominator: Int = 1
): LibVlcVideoLayout? {
    if (mode == LibVlcResizeMode.FIT) return LibVlcVideoLayout(null, 0f)
    if (windowWidth <= 0 || windowHeight <= 0) return null
    return when (mode) {
        LibVlcResizeMode.FIT -> LibVlcVideoLayout(null, 0f)
        LibVlcResizeMode.STRETCH ->
            LibVlcVideoLayout("$windowWidth:$windowHeight", 0f)
        LibVlcResizeMode.ZOOM -> {
            if (videoWidth <= 0 || videoHeight <= 0 ||
                sarNumerator <= 0 || sarDenominator <= 0
            ) return null
            val effectiveWidth = videoWidth.toDouble() *
                sarNumerator.toDouble() / sarDenominator.toDouble()
            if (!effectiveWidth.isFinite() || effectiveWidth <= 0.0) return null
            val scale = max(
                windowWidth.toDouble() / effectiveWidth,
                windowHeight.toDouble() / videoHeight.toDouble()
            )
            if (!scale.isFinite() || scale <= 0.0 || scale > 32.0) return null
            LibVlcVideoLayout(null, scale.toFloat())
        }
    }
}
