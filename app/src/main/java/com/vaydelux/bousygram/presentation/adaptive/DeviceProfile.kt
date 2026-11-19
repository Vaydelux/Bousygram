package com.vaydelux.bousygram.presentation.adaptive

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DeviceProfile {
    PhonePortrait,
    PhoneLandscape,
    Tablet,
    Tv
}

enum class WindowLayoutType {
    TV,
    Tablet,
    Phone
}

/**
 * Fine-grained viewport tiers covering compact phones, standard phones,
 * landscape phones, tablets, 32"-43" 720p/1080p TVs, 43"-65" 4K Google TVs
 * (960x540dp canvas), and 65"-85"+ large-canvas UHD TVs (1200-1920dp).
 */
enum class BousygramDisplayTier {
    PhoneCompact,
    PhoneStandard,
    PhoneLandscape,
    TabletMedium,
    TabletExpanded,
    TvCompact,
    TvStandard,
    TvLarge
}

/** Controls how section tabs render icons and labels to eliminate text cut-offs. */
enum class TabPresentationMode {
    /** Active tab shows Icon + full label; inactive tabs show crisp Icon-only + tooltip. */
    ICON_EXPAND_ACTIVE,
    /** All tabs show Icon + concise label without abbreviations or truncation. */
    ICON_AND_CONCISE,
    /** Spacious tablets & TVs show Icon + full label + optional count pill. */
    ICON_AND_FULL
}

/**
 * Exact dp geometry and density contract resolved from real window metrics
 * (never CPU ABI). Used alongside existing BousygramResponsiveTokens.
 */
data class BousygramDisplaySpec(
    val tier: BousygramDisplayTier,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val gridGap: Dp,
    val cardMinWidth: Dp,
    val minControlHeight: Dp,
    val iconSizeNav: Dp,
    val iconSizeAction: Dp,
    val iconSizeHero: Dp,
    val playerHeroButtonSize: Dp,
    val playerControlButtonSize: Dp,
    val maxReadingWidth: Dp,
    val dialogMaxWidth: Dp,
    val tooltipMaxWidth: Dp,
    val overscanSafeHorizontal: Dp,
    val overscanSafeVertical: Dp,
    val tabPresentation: TabPresentationMode,
    val showTabBadges: Boolean,
    val isTv: Boolean,
    val isCompact: Boolean,
    val isShortWindow: Boolean
)

internal fun resolveDisplayTier(metrics: AdaptiveMetrics): BousygramDisplayTier {
    val w = metrics.effectiveWidthDp
    val h = metrics.effectiveHeightDp
    return when {
        metrics.isTv -> when {
            metrics.widthDp >= 1200.dp && metrics.heightDp >= 660.dp && metrics.fontScale <= 1.25f ->
                BousygramDisplayTier.TvLarge
            metrics.widthDp < 920.dp || metrics.heightDp < 520.dp || metrics.fontScale >= 1.35f ->
                BousygramDisplayTier.TvCompact
            else -> BousygramDisplayTier.TvStandard
        }
        metrics.profile == DeviceProfile.Tablet -> when {
            w >= 840.dp -> BousygramDisplayTier.TabletExpanded
            else -> BousygramDisplayTier.TabletMedium
        }
        metrics.profile == DeviceProfile.PhoneLandscape && h < 500.dp ->
            BousygramDisplayTier.PhoneLandscape
        w < 380.dp -> BousygramDisplayTier.PhoneCompact
        else -> BousygramDisplayTier.PhoneStandard
    }
}

internal fun resolveDisplaySpec(metrics: AdaptiveMetrics): BousygramDisplaySpec {
    val tier = resolveDisplayTier(metrics)
    val fontFactor = metrics.fontScale.coerceIn(1f, 1.5f)
    fun safeCard(base: Dp, pad: Dp): Dp =
        minOf(base * fontFactor, (metrics.widthDp - pad * 2).coerceAtLeast(1.dp))

    return when (tier) {
        BousygramDisplayTier.PhoneCompact -> BousygramDisplaySpec(
            tier = tier,
            horizontalPadding = 12.dp,
            verticalPadding = 10.dp,
            gridGap = 10.dp,
            cardMinWidth = safeCard(140.dp, 12.dp),
            minControlHeight = 48.dp,
            iconSizeNav = 20.dp,
            iconSizeAction = 18.dp,
            iconSizeHero = 24.dp,
            playerHeroButtonSize = 52.dp,
            playerControlButtonSize = 48.dp,
            maxReadingWidth = 540.dp,
            dialogMaxWidth = 340.dp,
            tooltipMaxWidth = 220.dp,
            overscanSafeHorizontal = 12.dp,
            overscanSafeVertical = 10.dp,
            tabPresentation = TabPresentationMode.ICON_EXPAND_ACTIVE,
            showTabBadges = false,
            isTv = false,
            isCompact = true,
            isShortWindow = metrics.isShortWindow
        )
        BousygramDisplayTier.PhoneStandard -> BousygramDisplaySpec(
            tier = tier,
            horizontalPadding = 16.dp,
            verticalPadding = 12.dp,
            gridGap = 12.dp,
            cardMinWidth = safeCard(152.dp, 16.dp),
            minControlHeight = 48.dp,
            iconSizeNav = 20.dp,
            iconSizeAction = 20.dp,
            iconSizeHero = 26.dp,
            playerHeroButtonSize = 54.dp,
            playerControlButtonSize = 48.dp,
            maxReadingWidth = 600.dp,
            dialogMaxWidth = 420.dp,
            tooltipMaxWidth = 248.dp,
            overscanSafeHorizontal = 16.dp,
            overscanSafeVertical = 12.dp,
            tabPresentation = TabPresentationMode.ICON_AND_CONCISE,
            showTabBadges = false,
            isTv = false,
            isCompact = true,
            isShortWindow = metrics.isShortWindow
        )
        BousygramDisplayTier.PhoneLandscape -> BousygramDisplaySpec(
            tier = tier,
            horizontalPadding = 16.dp,
            verticalPadding = 8.dp,
            gridGap = 10.dp,
            cardMinWidth = safeCard(152.dp, 16.dp),
            minControlHeight = 48.dp,
            iconSizeNav = 20.dp,
            iconSizeAction = 18.dp,
            iconSizeHero = 24.dp,
            playerHeroButtonSize = 50.dp,
            playerControlButtonSize = 48.dp,
            maxReadingWidth = 760.dp,
            dialogMaxWidth = 520.dp,
            tooltipMaxWidth = 260.dp,
            overscanSafeHorizontal = 16.dp,
            overscanSafeVertical = 8.dp,
            tabPresentation = TabPresentationMode.ICON_AND_CONCISE,
            showTabBadges = metrics.effectiveWidthDp >= 640.dp,
            isTv = false,
            isCompact = true,
            isShortWindow = true
        )
        BousygramDisplayTier.TabletMedium -> BousygramDisplaySpec(
            tier = tier,
            horizontalPadding = 24.dp,
            verticalPadding = 16.dp,
            gridGap = 14.dp,
            cardMinWidth = safeCard(184.dp, 24.dp),
            minControlHeight = 48.dp,
            iconSizeNav = 22.dp,
            iconSizeAction = 20.dp,
            iconSizeHero = 28.dp,
            playerHeroButtonSize = 56.dp,
            playerControlButtonSize = 48.dp,
            maxReadingWidth = 840.dp,
            dialogMaxWidth = 560.dp,
            tooltipMaxWidth = 280.dp,
            overscanSafeHorizontal = 24.dp,
            overscanSafeVertical = 16.dp,
            tabPresentation = TabPresentationMode.ICON_AND_FULL,
            showTabBadges = true,
            isTv = false,
            isCompact = false,
            isShortWindow = metrics.isShortWindow
        )
        BousygramDisplayTier.TabletExpanded -> BousygramDisplaySpec(
            tier = tier,
            horizontalPadding = 28.dp,
            verticalPadding = 18.dp,
            gridGap = 16.dp,
            cardMinWidth = safeCard(196.dp, 28.dp),
            minControlHeight = 48.dp,
            iconSizeNav = 22.dp,
            iconSizeAction = 20.dp,
            iconSizeHero = 30.dp,
            playerHeroButtonSize = 58.dp,
            playerControlButtonSize = 50.dp,
            maxReadingWidth = 1040.dp,
            dialogMaxWidth = 680.dp,
            tooltipMaxWidth = 300.dp,
            overscanSafeHorizontal = 28.dp,
            overscanSafeVertical = 18.dp,
            tabPresentation = TabPresentationMode.ICON_AND_FULL,
            showTabBadges = true,
            isTv = false,
            isCompact = false,
            isShortWindow = metrics.isShortWindow
        )
        BousygramDisplayTier.TvCompact -> BousygramDisplaySpec(
            tier = tier,
            horizontalPadding = 28.dp,
            verticalPadding = 14.dp,
            gridGap = 14.dp,
            cardMinWidth = safeCard(208.dp, 28.dp),
            minControlHeight = 52.dp,
            iconSizeNav = 20.dp,
            iconSizeAction = 20.dp,
            iconSizeHero = 28.dp,
            playerHeroButtonSize = 56.dp,
            playerControlButtonSize = 50.dp,
            maxReadingWidth = 880.dp,
            dialogMaxWidth = 640.dp,
            tooltipMaxWidth = 280.dp,
            overscanSafeHorizontal = 28.dp,
            overscanSafeVertical = 18.dp,
            tabPresentation = TabPresentationMode.ICON_AND_CONCISE,
            showTabBadges = metrics.effectiveWidthDp >= 640.dp,
            isTv = true,
            isCompact = false,
            isShortWindow = metrics.isShortWindow
        )
        BousygramDisplayTier.TvStandard -> BousygramDisplaySpec(
            tier = tier,
            horizontalPadding = 32.dp,
            verticalPadding = 18.dp,
            gridGap = 16.dp,
            cardMinWidth = safeCard(208.dp, 32.dp),
            minControlHeight = 56.dp,
            iconSizeNav = 22.dp,
            iconSizeAction = 20.dp,
            iconSizeHero = 30.dp,
            playerHeroButtonSize = 60.dp,
            playerControlButtonSize = 52.dp,
            maxReadingWidth = 960.dp,
            dialogMaxWidth = 720.dp,
            tooltipMaxWidth = 320.dp,
            overscanSafeHorizontal = 32.dp,
            overscanSafeVertical = 22.dp,
            tabPresentation = TabPresentationMode.ICON_AND_FULL,
            showTabBadges = true,
            isTv = true,
            isCompact = false,
            isShortWindow = metrics.isShortWindow
        )
        BousygramDisplayTier.TvLarge -> BousygramDisplaySpec(
            tier = tier,
            horizontalPadding = 48.dp,
            verticalPadding = 26.dp,
            gridGap = 20.dp,
            cardMinWidth = safeCard(244.dp, 48.dp),
            minControlHeight = 58.dp,
            iconSizeNav = 24.dp,
            iconSizeAction = 22.dp,
            iconSizeHero = 34.dp,
            playerHeroButtonSize = 66.dp,
            playerControlButtonSize = 56.dp,
            maxReadingWidth = 1160.dp,
            dialogMaxWidth = 860.dp,
            tooltipMaxWidth = 360.dp,
            overscanSafeHorizontal = 48.dp,
            overscanSafeVertical = 27.dp,
            tabPresentation = TabPresentationMode.ICON_AND_FULL,
            showTabBadges = true,
            isTv = true,
            isCompact = false,
            isShortWindow = false
        )
    }
}

@Composable
fun rememberDisplaySpec(): BousygramDisplaySpec =
    resolveDisplaySpec(rememberAdaptiveMetrics())

data class AdaptiveMetrics(
    val profile: DeviceProfile,
    val widthDp: Dp,
    val heightDp: Dp,
    val fontScale: Float = 1f,
    val keyboardNavigation: Boolean = false
) {
    val isTv: Boolean = profile == DeviceProfile.Tv
    val isCompact: Boolean = profile == DeviceProfile.PhonePortrait || profile == DeviceProfile.PhoneLandscape
    val effectiveWidthDp: Dp = widthDp / fontScale.coerceIn(1f, 2f)
    val effectiveHeightDp: Dp = heightDp / fontScale.coerceIn(1f, 2f)
    val isShortWindow: Boolean = effectiveHeightDp < 480.dp
    val useTvChrome: Boolean = profile == DeviceProfile.Tv || (effectiveWidthDp >= 900.dp && effectiveHeightDp >= 520.dp)

    val layoutType: WindowLayoutType = when {
        isTv -> WindowLayoutType.TV
        minOf(widthDp, heightDp) >= 600.dp -> WindowLayoutType.Tablet
        else -> WindowLayoutType.Phone
    }
    val isTvLayout: Boolean = layoutType == WindowLayoutType.TV
    val isTabletLayout: Boolean = layoutType == WindowLayoutType.Tablet
    val isPhoneLayout: Boolean = layoutType == WindowLayoutType.Phone
}

/**
 * Device classification deliberately never checks Build.SUPPORTED_ABIS or CPU bitness.
 * Inexpensive third-party Android TV firmware may omit UI_MODE_TYPE_TELEVISION
 * while still declaring FEATURE_LEANBACK or FEATURE_TELEVISION.
 */
internal fun isTelevisionDevice(
    currentModeType: Int?,
    configurationModeType: Int,
    hasLeanback: Boolean,
    hasTelevisionFeature: Boolean
): Boolean = currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
    configurationModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
    hasLeanback || hasTelevisionFeature

internal fun resolveDeviceProfile(
    isTelevision: Boolean,
    widthDp: Dp,
    heightDp: Dp
): DeviceProfile = when {
    isTelevision -> DeviceProfile.Tv
    minOf(widthDp, heightDp) >= 600.dp -> DeviceProfile.Tablet
    widthDp > heightDp -> DeviceProfile.PhoneLandscape
    else -> DeviceProfile.PhonePortrait
}

/**
 * A short landscape phone uses a rail so neither a top header nor a bottom
 * navigation bar eats the available media height. Tablet rails remain standard.
 */
internal fun shouldUseSideRail(profile: DeviceProfile, heightDp: Dp): Boolean =
    profile == DeviceProfile.Tablet ||
        (profile == DeviceProfile.PhoneLandscape && heightDp < 520.dp)

@Composable
fun rememberAdaptiveMetrics(): AdaptiveMetrics {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { size.width.toDp() }
    val heightDp = with(density) { size.height.toDp() }
    val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val packageManager = context.packageManager
    val isTv = isTelevisionDevice(
        currentModeType = manager?.currentModeType,
        configurationModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK,
        hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
        hasTelevisionFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    )
    val profile = resolveDeviceProfile(isTv, widthDp, heightDp)
    return AdaptiveMetrics(profile, widthDp, heightDp, configuration.fontScale,
        LocalInputModeManager.current.inputMode == InputMode.Keyboard)
}

