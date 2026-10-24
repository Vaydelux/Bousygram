package com.vaydelux.bousygram.presentation.adaptive

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
    val heightDp: Dp
) {
    val isTv: Boolean = profile == DeviceProfile.Tv
    val isCompact: Boolean = profile == DeviceProfile.PhonePortrait || profile == DeviceProfile.PhoneLandscape
    val useTvChrome: Boolean = profile == DeviceProfile.Tv || (widthDp >= 900.dp && heightDp >= 520.dp)

    val layoutType: WindowLayoutType = when {
        isTv -> WindowLayoutType.TV
        minOf(widthDp, heightDp) >= 600.dp -> WindowLayoutType.Tablet
        else -> WindowLayoutType.Phone
    }
    val isTvLayout: Boolean = layoutType == WindowLayoutType.TV
    val isTabletLayout: Boolean = layoutType == WindowLayoutType.Tablet
    val isPhoneLayout: Boolean = layoutType == WindowLayoutType.Phone
}

@Composable
fun rememberAdaptiveMetrics(): AdaptiveMetrics {
    val context = LocalContext.current
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { size.width.toDp() }
    val heightDp = with(density) { size.height.toDp() }
    val isTv = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
        ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val shortest = minOf(widthDp, heightDp)
    val profile = when {
        isTv -> DeviceProfile.Tv
        shortest >= 600.dp -> DeviceProfile.Tablet
        widthDp > heightDp -> DeviceProfile.PhoneLandscape
        else -> DeviceProfile.PhonePortrait
    }
    return AdaptiveMetrics(profile, widthDp, heightDp)
}

