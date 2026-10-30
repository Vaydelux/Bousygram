package com.vaydelux.bousygram.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemePresetTest {
    @Test fun storedThemesRoundTripAndOldMidnightIdStillWorks() {
        BousygramThemePreset.entries.forEach { preset ->
            assertEquals(preset, BousygramThemePreset.fromId(preset.id))
        }
        assertEquals(BousygramThemePreset.DARK, BousygramThemePreset.fromId("midnight"))
        assertEquals(BousygramThemePreset.DARK, BousygramThemePreset.fromId("invalid"))
    }

    @Test fun highContrastIsDistinctFromLightAndDefault() {
        assertNotEquals(BousygramThemePreset.DARK.primary, BousygramThemePreset.HIGH_CONTRAST.primary)
        assertNotEquals(BousygramThemePreset.LIGHT.background, BousygramThemePreset.DARK.background)
    }
}
