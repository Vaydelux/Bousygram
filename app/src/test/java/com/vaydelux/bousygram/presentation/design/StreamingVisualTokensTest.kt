package com.vaydelux.bousygram.presentation.design

import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.adaptive.AdaptiveMetrics
import com.vaydelux.bousygram.presentation.adaptive.DeviceProfile
import com.vaydelux.bousygram.presentation.design.contract.streamingLayoutMetrics
import com.vaydelux.bousygram.presentation.design.contract.streamingProgressFraction
import com.vaydelux.bousygram.presentation.design.contract.streamingVisualTokens
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.schemeFor
import com.vaydelux.bousygram.presentation.theme.themeContrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase-1 contract checks; no emulator, providers or credentials needed. */
class StreamingVisualTokensTest {
    @Test fun allInstalledThemesDriveReadableBrandNeutralRoles() {
        BousygramThemePreset.entries.forEach { preset ->
            val scheme = schemeFor(preset)
            val ui = streamingVisualTokens(scheme)
            assertEquals(scheme.primary, ui.action)
            assertEquals(scheme.background, ui.background)
            assertEquals(scheme.onPrimary, ui.onAction)
            assertEquals(scheme.primaryContainer, ui.selected)
            assertEquals(scheme.onPrimaryContainer, ui.onSelected)
            assertTrue("${preset.id}: foreground unreadable on surface",
                themeContrastRatio(ui.textPrimary, ui.surface) >= 4.5)
            assertTrue("${preset.id}: secondary foreground unreadable on raised surface",
                themeContrastRatio(ui.textSecondary, ui.raised) >= 4.5)
            assertTrue("${preset.id}: active text unreadable",
                themeContrastRatio(ui.onSelected, ui.selected) >= 4.5)
            assertNotEquals("${preset.id}: focus must differ from selected fill", ui.focus, ui.selected)
        }
    }

    @Test fun responsiveGeometryDelegatesToExistingDeviceMetrics() {
        val phone = streamingLayoutMetrics(AdaptiveMetrics(DeviceProfile.PhonePortrait, 360.dp, 740.dp))
        val shortLandscape = streamingLayoutMetrics(AdaptiveMetrics(DeviceProfile.PhoneLandscape, 680.dp, 400.dp))
        val tv = streamingLayoutMetrics(AdaptiveMetrics(DeviceProfile.Tv, 1280.dp, 720.dp))
        assertEquals(16.dp, phone.pagePadding)
        assertEquals(32.dp, tv.pagePadding)
        assertEquals(56.dp, tv.minControlHeight)
        assertEquals(48.dp, phone.minControlHeight)
        assertEquals(3.dp, tv.focusOutline)
        assertTrue(shortLandscape.cardMinWidth <= 680.dp)
        assertTrue(tv.cardMinWidth > phone.cardMinWidth)
    }

    @Test fun unknownOrInvalidDurationsNeverPretendProgress() {
        assertNull(streamingProgressFraction(0L, 0L))
        assertNull(streamingProgressFraction(100L, -1L))
        assertNull(streamingProgressFraction(-1L, 100L))
        assertEquals(0f, streamingProgressFraction(0L, 100L)!!)
        assertEquals(.4f, streamingProgressFraction(40L, 100L)!!)
        assertEquals(1f, streamingProgressFraction(200L, 100L)!!)
        assertEquals(1f, streamingProgressFraction(Long.MAX_VALUE, 1L)!!)
    }
}
