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

