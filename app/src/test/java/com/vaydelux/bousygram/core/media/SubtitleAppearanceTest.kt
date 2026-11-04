package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleAppearanceTest {
    @Test fun invalidPersistedPresetFallsBackToReadableWhite() {
        assertEquals(SubtitleColorPreset.WHITE, SubtitleColorPreset.fromStored("INVALID"))
        assertEquals(SubtitleColorPreset.YELLOW, SubtitleColorPreset.fromStored("YELLOW"))
        assertEquals(SubtitleColorPreset.BLACK, SubtitleColorPreset.fromStored("BLACK"))
    }

    @Test fun subtitleFontSizesStayInSafeTvAndPhoneRange() {
        val small = SubtitleAppearance(percent = -500)
        val large = SubtitleAppearance(percent = 9999)
        assertEquals(75, small.safePercent)
        assertEquals(200, large.safePercent)
        assertEquals(75, large.changedPercent(-99).percent)
        assertEquals(200, small.changedPercent(999).percent)
        assertTrue(large.exoFractionOfHeight > small.exoFractionOfHeight)
        assertEquals(2.0, large.mpvScale, 0.001)
    }

    @Test fun allColorPresetsHaveVisibleBackgroundContrast() {
        SubtitleColorPreset.entries.forEach {
            assertTrue(it.textColor != it.backgroundColor)
            assertTrue(it.mpvTextRgb.startsWith("#"))
            assertTrue(it.mpvEdgeRgb.startsWith("#"))
        }
    }
}
