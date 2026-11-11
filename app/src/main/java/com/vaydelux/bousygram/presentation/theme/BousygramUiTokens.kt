package com.vaydelux.bousygram.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.vaydelux.bousygram.presentation.adaptive.DeviceProfile

/**
 * Shared, semantic UI tokens for TV, phone, and tablet. All text uses the
 * matching MaterialTheme.colorScheme "on" role, never a hardcoded text color.
 */
object BousygramUiTokens {
    val touchTarget = 48.dp
    val tvFocusOutline = 3.dp
    val compactSpacing = 8.dp
    val contentSpacing = 16.dp
    val sectionSpacing = 24.dp
    val phonePagePadding = 16.dp
    val tvPagePadding = 32.dp
    val cardCorner = 20.dp
    val panelCorner = 24.dp
    val tabCorner = 14.dp
    val cardGridPhone = 12.dp
    val cardGridTv = 16.dp
    val cardGridTablet = 14.dp
    val cardMinPhone = 152.dp
    val cardMinTablet = 184.dp
    val cardMinTv = 208.dp
    val folderMinPhone = 164.dp
    val folderMinTv = 176.dp
    val focusScale = 1.018f
    const val focusDurationMs = 145
    val compactControlHeight = 48.dp
    val tvControlHeight = 56.dp
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

/**
 * One visual scale for all Android builds. Only screen real estate and input
 * affordance change; color, typography, card shape, and focus language do not.
 * CPU ABI (armeabi-v7a vs arm64-v8a) must never choose UI resources.
 */
data class BousygramResponsiveTokens(
    val pagePadding: Dp,
    val gridGap: Dp,
    val cardMinWidth: Dp,
    val folderMinHeight: Dp,
    val minControlHeight: Dp
)

fun bousygramResponsiveTokens(profile: DeviceProfile): BousygramResponsiveTokens =
    when (profile) {
        DeviceProfile.Tv -> BousygramResponsiveTokens(
            pagePadding = BousygramUiTokens.tvPagePadding,
            gridGap = BousygramUiTokens.cardGridTv,
            cardMinWidth = BousygramUiTokens.cardMinTv,
            folderMinHeight = BousygramUiTokens.folderMinTv,
            minControlHeight = BousygramUiTokens.tvControlHeight
        )
        DeviceProfile.Tablet -> BousygramResponsiveTokens(
            pagePadding = BousygramUiTokens.sectionSpacing,
            gridGap = BousygramUiTokens.cardGridTablet,
            cardMinWidth = BousygramUiTokens.cardMinTablet,
            folderMinHeight = BousygramUiTokens.folderMinPhone,
            minControlHeight = BousygramUiTokens.compactControlHeight
        )
        DeviceProfile.PhonePortrait, DeviceProfile.PhoneLandscape -> BousygramResponsiveTokens(
            pagePadding = BousygramUiTokens.phonePagePadding,
            gridGap = BousygramUiTokens.cardGridPhone,
            cardMinWidth = BousygramUiTokens.cardMinPhone,
            folderMinHeight = BousygramUiTokens.folderMinPhone,
            minControlHeight = BousygramUiTokens.compactControlHeight
        )
    }

/**
 * The media grid's visible cards—not decorative headers—own scarce vertical space.
 * A short TV keeps two readable title lines but omits duplicate source metadata
 * (the source is already in the toolbar). A short landscape phone uses one line.
 * Standard/portrait viewports keep all metadata visible.
 */
internal enum class MediaCardDensity { STANDARD, SHORT_TV, SHORT_LANDSCAPE }

internal fun mediaCardDensity(isTv: Boolean, widthDp: Dp, heightDp: Dp): MediaCardDensity =
    when {
        isTv && heightDp < 660.dp -> MediaCardDensity.SHORT_TV
        widthDp > heightDp && heightDp < 560.dp -> MediaCardDensity.SHORT_LANDSCAPE
        else -> MediaCardDensity.STANDARD
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
