package com.vaydelux.bousygram.presentation.theme

import com.vaydelux.bousygram.data.store.SettingsImportPolicy
import com.vaydelux.bousygram.presentation.components.BousygramGlassLevel
import com.vaydelux.bousygram.presentation.components.formatDynamicIslandCompactTitle
import com.vaydelux.bousygram.presentation.components.resolveLiquidGlassAlpha
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit contracts for iOS 27/28 Liquid Glass tokens, Dynamic Island labels, and Reduce Transparency. */
class LiquidGlassDesignContractTest {

    @Test
    fun reduceTransparencyAndHighContrastGuaranteeHighLegibilityOpacity() {
        for (level in BousygramGlassLevel.entries) {
            for (light in listOf(false, true)) {
                val translucent = resolveLiquidGlassAlpha(level, isLight = light, isHighContrast = false, reduceTransparency = false)
                val reduced = resolveLiquidGlassAlpha(level, isLight = light, isHighContrast = false, reduceTransparency = true)
                val highContrast = resolveLiquidGlassAlpha(level, isLight = light, isHighContrast = true, reduceTransparency = false)

                assertTrue("Translucent alpha for $level must stay readable", translucent in 0.60f..0.95f)
                assertTrue("Reduce Transparency alpha for $level must be >= 0.96", reduced >= 0.96f)
                assertTrue("High Contrast alpha for $level must be >= 0.99", highContrast >= 0.99f)
                assertTrue("Reduce Transparency must be at least as opaque as translucent", reduced >= translucent)
            }
        }
    }

    @Test
    fun iosAccentTintsMapToWcagReadableDarkAndLightPresets() {
        for (tint in BousygramIosAccentTint.entries) {
            for (light in listOf(false, true)) {
                val preset = tint.presetFor(lightMode = light)
                assertEquals(light, preset.isLightPreset)
                val c = schemeFor(preset)
                assertTrue(
                    "${tint.id} (${preset.id}) background contrast < 4.5",
                    themeContrastRatio(c.onBackground, c.background) >= 4.5
                )
                assertTrue(
                    "${tint.id} (${preset.id}) surface contrast < 4.5",
                    themeContrastRatio(c.onSurface, c.surfaceContainerHigh) >= 4.5
                )
                assertTrue(
                    "${tint.id} (${preset.id}) primary contrast < 4.5",
                    themeContrastRatio(c.onPrimary, c.primary) >= 4.5
                )
            }
        }
        assertEquals(BousygramIosAccentTint.HALO_CYAN, BousygramIosAccentTint.fromPreset(BousygramThemePreset.MIDNIGHT_GLASS))
        assertEquals(BousygramIosAccentTint.COBALT_BLUE, BousygramIosAccentTint.fromPreset(BousygramThemePreset.CUPERTINO))
        assertEquals(BousygramIosAccentTint.EMERALD, BousygramIosAccentTint.fromPreset(BousygramThemePreset.EVERGREEN))
        assertEquals(BousygramIosAccentTint.EMBER, BousygramIosAccentTint.fromPreset(BousygramThemePreset.EMBER))
    }

    @Test
    fun liquidGlassStyleSpecPreservesDistinctGeometryAndFlags() {
        val ios27 = styleSpecFor(BousygramDesignStyle.IPHONE_GLASS)
        val halo = styleSpecFor(BousygramDesignStyle.CINEMA_HALO)
        assertTrue(ios27.isLiquidGlass)
        assertFalse(halo.isLiquidGlass)
        assertTrue(ios27.floatingBottomBar)
        assertTrue(ios27.segmentedTabs)
        assertTrue(ios27.useSpecularHighlight)
        assertTrue(ios27.playerDockFloatingCapsule)
    }

    @Test
    fun dynamicIslandCompactLabelTruncatesCleanlyWithoutNewlines() {
        assertEquals("Saved Messages", formatDynamicIslandCompactTitle("  Saved   Messages  ", 24))
        assertEquals("Online", formatDynamicIslandCompactTitle("Online", 18))
        val truncated = formatDynamicIslandCompactTitle("A very long Telegram channel title here", 18)
        assertTrue(truncated.length <= 18)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun settingsImportPolicyRoundTripsReduceTransparencyAndRejectsBadTypes() {
        val enabledDoc = JSONObject()
            .put("version", 1)
            .put("visualReduceTransparency", true)
            .toString()
        val disabledDoc = JSONObject()
            .put("version", 1)
            .put("visualReduceTransparency", false)
            .toString()
        val omittedDoc = JSONObject().put("version", 1).toString()

        assertEquals(true, SettingsImportPolicy.parse(enabledDoc, null).visualReduceTransparency)
        assertEquals(false, SettingsImportPolicy.parse(disabledDoc, null).visualReduceTransparency)
        assertNull(SettingsImportPolicy.parse(omittedDoc, null).visualReduceTransparency)

        for (invalid in listOf("\"true\"", "1", "null", "[]")) {
            val bad = """{"version":1,"visualReduceTransparency":$invalid}"""
            assertThrows(IllegalArgumentException::class.java) {
                SettingsImportPolicy.parse(bad, null)
            }
        }
    }
}
