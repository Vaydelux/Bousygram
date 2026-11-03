package com.vaydelux.bousygram.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared, semantic UI tokens for TV, phone, and tablet. All text uses the
 * matching MaterialTheme.colorScheme "on" role, never a hardcoded text color.
 */
object BousygramUiTokens {
    val touchTarget = 48.dp
    val tvFocusOutline = 3.dp
    val compactSpacing = 8.dp
    val cardCorner = 18.dp
    val panelCorner = 22.dp
    const val hudFadeInMs = 210
    const val hudFadeOutMs = 240
    const val hudHideAfterInteractionMs = 4_000L
    const val darkHudOpacity = 0.90f
    const val lightHudOpacity = 0.96f
    const val contrastHudOpacity = 0.99f

    /** The cyan/blue/amber roles come from the approved Chow Chow mascot. */
    val mascotCyan = Color(0xFF37D7F2)
    val mascotBlue = Color(0xFF2289F9)
    val chowGold = Color(0xFFFFC67D)
}

/** WCAG relative contrast for *opaque* UI tokens; not a screen-reader heuristic. */
internal fun themeContrastRatio(foreground: Color, background: Color): Double {
    fun channel(value: Float): Double {
        val x = value.toDouble().coerceIn(0.0, 1.0)
        return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
    }
    fun luminance(c: Color) =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    val a = luminance(foreground)
    val b = luminance(background)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/** Select surface opacity without sacrificing the high-contrast reading mode. */
internal fun playerHudSurfaceOpacity(background: Color): Float = when (background) {
    BousygramThemePreset.HIGH_CONTRAST.background -> BousygramUiTokens.contrastHudOpacity
    BousygramThemePreset.LIGHT.background -> BousygramUiTokens.lightHudOpacity
    else -> BousygramUiTokens.darkHudOpacity
}
