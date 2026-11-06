package com.vaydelux.bousygram.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    @Test fun contrastModeHasOpaqueSurfacesAndDistinctReadableContainers() {
        val high = schemeFor(BousygramThemePreset.HIGH_CONTRAST)
        val light = schemeFor(BousygramThemePreset.LIGHT)
        assertEquals(BousygramThemePreset.HIGH_CONTRAST.background, high.background)
        assertNotEquals(light.surface, high.surface)
        assertTrue(themeContrastRatio(high.onSurface, high.surfaceContainerHigh) >= 7.0)
        assertTrue(themeContrastRatio(high.onBackground, high.background) >= 7.0)
        assertTrue(themeContrastRatio(high.onPrimary, high.primary) >= 7.0)
    }

    @Test fun hudOpacityRespectsHighContrastWhileShowingMoreVideoInDarkMode() {
        assertEquals(0.90f, playerHudSurfaceOpacity(BousygramThemePreset.DARK.background), 0.001f)
        assertEquals(0.96f, playerHudSurfaceOpacity(BousygramThemePreset.LIGHT.background), 0.001f)
        assertEquals(0.99f, playerHudSurfaceOpacity(BousygramThemePreset.HIGH_CONTRAST.background), 0.001f)
    }

    @Test fun semanticTextOnSurfacesRemainsReadableInEveryTheme() {
        BousygramThemePreset.entries.forEach { preset ->
            val c = schemeFor(preset)
            val pairs = listOf(
                "background" to themeContrastRatio(c.onBackground, c.background),
                "surface" to themeContrastRatio(c.onSurface, c.surface),
                "surface low" to themeContrastRatio(c.onSurface, c.surfaceContainerLow),
                "surface high" to themeContrastRatio(c.onSurface, c.surfaceContainerHigh),
                "surface variant" to themeContrastRatio(c.onSurfaceVariant, c.surfaceVariant),
                "primary" to themeContrastRatio(c.onPrimary, c.primary),
                "primary container" to themeContrastRatio(c.onPrimaryContainer, c.primaryContainer),
                "secondary" to themeContrastRatio(c.onSecondary, c.secondary),
                "tertiary" to themeContrastRatio(c.onTertiary, c.tertiary),
                "error" to themeContrastRatio(c.onError, c.error)
            )
            pairs.forEach { (role, ratio) ->
                assertTrue("${preset.id} on $role has contrast $ratio (< 4.5)", ratio >= 4.5)
            }
        }
    }

}
