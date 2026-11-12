package com.vaydelux.bousygram.presentation.adaptive

import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.bousygramResponsiveTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Form-factor presentation must depend on UI mode and real window dimensions,
 * never CPU ABI. These contracts also apply to third-party 32-bit TV boxes.
 */
class UniformDesignMetricsTest {
    @Test fun televisionUiModeIsDetected() {
        assertTrue(isTelevisionDevice(
            Configuration.UI_MODE_TYPE_TELEVISION,
            Configuration.UI_MODE_TYPE_NORMAL, false, false
        ))
        assertTrue(isTelevisionDevice(
            null, Configuration.UI_MODE_TYPE_TELEVISION, false, false
        ))
    }

    @Test fun leanbackFirmwareFallbackPreventsTelevisionFromUsingPhoneChrome() {
        assertTrue(isTelevisionDevice(
            Configuration.UI_MODE_TYPE_NORMAL,
            Configuration.UI_MODE_TYPE_NORMAL,
            hasLeanback = true,
            hasTelevisionFeature = false
        ))
        assertTrue(isTelevisionDevice(
            null, Configuration.UI_MODE_TYPE_NORMAL,
            hasLeanback = false,
            hasTelevisionFeature = true
        ))
        assertEquals(DeviceProfile.Tv, resolveDeviceProfile(true, 960.dp, 540.dp))
        assertEquals(DeviceProfile.Tv, resolveDeviceProfile(true, 720.dp, 400.dp))
        assertFalse(isTelevisionDevice(
            Configuration.UI_MODE_TYPE_NORMAL,
            Configuration.UI_MODE_TYPE_NORMAL, false, false
        ))
    }

    @Test fun actualPhonesAndTabletsKeepAdaptiveSizingWithoutTvAssumptions() {
        assertEquals(DeviceProfile.PhonePortrait, resolveDeviceProfile(false, 393.dp, 852.dp))
        assertEquals(DeviceProfile.PhoneLandscape, resolveDeviceProfile(false, 852.dp, 393.dp))
        assertEquals(DeviceProfile.Tablet, resolveDeviceProfile(false, 1024.dp, 768.dp))
    }

    @Test fun phoneLandscapeUsesCompactSideNavigationWhilePortraitRetainsBottomNavigation() {
        assertTrue(shouldUseSideRail(DeviceProfile.Tablet, 1000.dp))
        assertTrue(shouldUseSideRail(DeviceProfile.PhoneLandscape, 390.dp))
        assertTrue(shouldUseSideRail(DeviceProfile.PhoneLandscape, 480.dp))
        assertFalse(shouldUseSideRail(DeviceProfile.PhoneLandscape, 540.dp))
        assertFalse(shouldUseSideRail(DeviceProfile.PhonePortrait, 390.dp))
        assertFalse(shouldUseSideRail(DeviceProfile.Tv, 540.dp))
    }

    @Test fun sharedVisualTokensVaryOnlyWithScreenRealEstateNotArchitecture() {
        val tv = bousygramResponsiveTokens(DeviceProfile.Tv)
        val tablet = bousygramResponsiveTokens(DeviceProfile.Tablet)
        val phone = bousygramResponsiveTokens(DeviceProfile.PhonePortrait)
        val landscape = bousygramResponsiveTokens(DeviceProfile.PhoneLandscape)

        assertEquals(BousygramUiTokens.cardMinTv, tv.cardMinWidth)
        assertEquals(BousygramUiTokens.cardGridTv, tv.gridGap)
        assertEquals(BousygramUiTokens.tvPagePadding, tv.pagePadding)
        assertEquals(BousygramUiTokens.phonePagePadding, phone.pagePadding)
        assertEquals(phone, landscape)
        assertTrue(tv.cardMinWidth > tablet.cardMinWidth)
        assertTrue(tablet.cardMinWidth > phone.cardMinWidth)
        assertEquals(BousygramUiTokens.cardCorner, 8.dp)
        assertEquals(BousygramUiTokens.focusScale, 1.018f, 0.001f)
    }
}
