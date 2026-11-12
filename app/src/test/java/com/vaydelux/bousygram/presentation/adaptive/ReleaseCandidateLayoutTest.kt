package com.vaydelux.bousygram.presentation.adaptive

import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.theme.bousygramResponsiveTokens
import org.junit.Assert.*
import org.junit.Test

class ReleaseCandidateLayoutTest {
    @Test fun largeTextReducesChromeBudgetWithoutShrinkingTargets() {
        val window = AdaptiveMetrics(DeviceProfile.Tv, 960.dp, 540.dp, fontScale = 2f)
        assertTrue(window.isShortWindow)
        assertEquals(480.dp, window.effectiveWidthDp)
        val tokens = bousygramResponsiveTokens(window)
        assertEquals(56.dp, tokens.minControlHeight)
        assertTrue(tokens.cardMinWidth >= 208.dp)
        assertEquals(32.dp, tokens.pagePadding)
    }

    @Test fun narrowResizableWindowCanAlwaysFitOneCard() {
        for (width in listOf(240, 320, 393, 600, 1280)) {
            val window = AdaptiveMetrics(DeviceProfile.PhonePortrait, width.dp, 700.dp, 2f)
            val tokens = bousygramResponsiveTokens(window)
            assertTrue(tokens.cardMinWidth + tokens.pagePadding * 2 <= window.widthDp)
            assertTrue(tokens.minControlHeight >= 48.dp)
        }
    }

    @Test fun desktopChromeRequiresBothWidthAndHeightAfterTextScaling() {
        assertTrue(AdaptiveMetrics(DeviceProfile.Tablet, 1280.dp, 900.dp).useTvChrome)
        assertFalse(AdaptiveMetrics(DeviceProfile.PhoneLandscape, 1280.dp, 390.dp).useTvChrome)
        assertFalse(AdaptiveMetrics(DeviceProfile.Tablet, 1280.dp, 900.dp, 2f).useTvChrome)
    }
}
