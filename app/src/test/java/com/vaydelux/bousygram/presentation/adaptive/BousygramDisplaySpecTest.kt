package com.vaydelux.bousygram.presentation.adaptive

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.bousygramTypographyFor
import com.vaydelux.bousygram.presentation.theme.haloColorsFor
import com.vaydelux.bousygram.presentation.theme.schemeFor
import com.vaydelux.bousygram.presentation.theme.themeContrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies exact pixel/dp display tiers, touch/10-ft focus target floors,
 * anti-cutoff tab presentation rules, responsive typography floors, and
 * Cinema Halo tooltip contrast across phones, tablets, and 32"-85"+ TVs.
 */
class BousygramDisplaySpecTest {

    @Test
    fun allEightDisplayTiersResolveFromRealViewportMetrics() {
        assertEquals(
            BousygramDisplayTier.PhoneCompact,
            resolveDisplayTier(AdaptiveMetrics(DeviceProfile.PhonePortrait, 360.dp, 800.dp))
        )
        assertEquals(
            BousygramDisplayTier.PhoneCompact,
            resolveDisplayTier(AdaptiveMetrics(DeviceProfile.PhonePortrait, 393.dp, 852.dp, fontScale = 1.3f))
        )
        assertEquals(
            BousygramDisplayTier.PhoneStandard,
            resolveDisplayTier(AdaptiveMetrics(DeviceProfile.PhonePortrait, 412.dp, 915.dp))
        )
        assertEquals(
            BousygramDisplayTier.PhoneLandscape,
            resolveDisplayTier(AdaptiveMetrics(DeviceProfile.PhoneLandscape, 844.dp, 390.dp))
        )
        assertEquals(
            BousygramDisplayTier.TabletMedium,
            resolveDisplayTier(AdaptiveMetrics(DeviceProfile.Tablet, 744.dp, 1133.dp))
        )
        assertEquals(
            BousygramDisplayTier.TabletExpanded,
            resolveDisplayTier(AdaptiveMetrics(DeviceProfile.Tablet, 1280.dp, 800.dp))
        )
        assertEquals(
            BousygramDisplayTier.TvCompact,
            resolveDisplayTier(AdaptiveMetrics(DeviceProfile.Tv, 800.dp, 450.dp))
        )
        assertEquals(
            BousygramDisplayTier.TvCompact,
            resolveDisplayTier(AdaptiveMetrics(DeviceProfile.Tv, 960.dp, 540.dp, fontScale = 1.4f))
        )
        assertEquals(
            BousygramDisplayTier.TvStandard,
            resolveDisplayTier(AdaptiveMetrics(DeviceProfile.Tv, 960.dp, 540.dp))
        )
        assertEquals(
            BousygramDisplayTier.TvLarge,
            resolveDisplayTier(AdaptiveMetrics(DeviceProfile.Tv, 1280.dp, 720.dp))
        )
    }

    @Test
    fun touchAndTenFootTvControlTargetsMeetMinimumFloors() {
        val sampleMetrics = listOf(
            AdaptiveMetrics(DeviceProfile.PhonePortrait, 320.dp, 640.dp),
            AdaptiveMetrics(DeviceProfile.PhonePortrait, 412.dp, 915.dp),
            AdaptiveMetrics(DeviceProfile.PhoneLandscape, 844.dp, 390.dp),
            AdaptiveMetrics(DeviceProfile.Tablet, 768.dp, 1024.dp),
            AdaptiveMetrics(DeviceProfile.Tablet, 1280.dp, 800.dp),
            AdaptiveMetrics(DeviceProfile.Tv, 800.dp, 450.dp),
            AdaptiveMetrics(DeviceProfile.Tv, 960.dp, 540.dp),
            AdaptiveMetrics(DeviceProfile.Tv, 1280.dp, 720.dp)
        )

        sampleMetrics.forEach { metrics ->
            val spec = resolveDisplaySpec(metrics)
            if (spec.isTv) {
                assertTrue("${spec.tier} minControlHeight must be >= 52dp", spec.minControlHeight >= 52.dp)
                assertTrue("${spec.tier} overscanSafeHorizontal must be >= 28dp", spec.overscanSafeHorizontal >= 28.dp)
            } else {
                assertTrue("${spec.tier} minControlHeight must be >= 48dp", spec.minControlHeight >= 48.dp)
            }
            assertTrue(
                "${spec.tier} player hero button must be larger than standard control",
                spec.playerHeroButtonSize > spec.playerControlButtonSize
            )
            assertTrue(
                "${spec.tier} cardMinWidth must fit inside padded viewport",
                spec.cardMinWidth <= metrics.widthDp - spec.horizontalPadding * 2
            )
        }
    }

    @Test
    fun tabPresentationPreventsTextCutoffOnCompactPhonesAndUnlocksBadgesOnWideScreens() {
        val phoneCompact = resolveDisplaySpec(AdaptiveMetrics(DeviceProfile.PhonePortrait, 360.dp, 800.dp))
        val phoneStandard = resolveDisplaySpec(AdaptiveMetrics(DeviceProfile.PhonePortrait, 412.dp, 915.dp))
        val tablet = resolveDisplaySpec(AdaptiveMetrics(DeviceProfile.Tablet, 1024.dp, 768.dp))
        val tvStandard = resolveDisplaySpec(AdaptiveMetrics(DeviceProfile.Tv, 960.dp, 540.dp))
        val tvLarge = resolveDisplaySpec(AdaptiveMetrics(DeviceProfile.Tv, 1280.dp, 720.dp))

        assertEquals(TabPresentationMode.ICON_EXPAND_ACTIVE, phoneCompact.tabPresentation)
        assertFalse(phoneCompact.showTabBadges)

        assertEquals(TabPresentationMode.ICON_AND_CONCISE, phoneStandard.tabPresentation)
        assertEquals(TabPresentationMode.ICON_AND_FULL, tablet.tabPresentation)
        assertTrue(tablet.showTabBadges)

        assertEquals(TabPresentationMode.ICON_AND_FULL, tvStandard.tabPresentation)
        assertTrue(tvStandard.showTabBadges)
        assertTrue(tvLarge.horizontalPadding > tvStandard.horizontalPadding)
        assertTrue(tvLarge.maxReadingWidth > tvStandard.maxReadingWidth)
    }

    @Test
    fun typographyEnforcesTenFootTvReadabilityFloorsAndDistinctFontFamilies() {
        val tvCompact = bousygramTypographyFor(BousygramDisplayTier.TvCompact)
        val tvStandard = bousygramTypographyFor(BousygramDisplayTier.TvStandard)
        val tvLarge = bousygramTypographyFor(BousygramDisplayTier.TvLarge)

        listOf(tvCompact, tvStandard, tvLarge).forEach { typo ->
            assertTrue("TV labelSmall must be >= 12sp", typo.labelSmall.fontSize >= 12.sp)
            assertTrue("TV bodySmall must be >= 13sp", typo.bodySmall.fontSize >= 13.sp)
            assertTrue("TV bodyMedium must be >= 14.5sp", typo.bodyMedium.fontSize >= 14.5.sp)
            assertNotEquals(
                "Heading font and reading font must stay distinct",
                typo.headlineLarge.fontFamily,
                typo.bodyMedium.fontFamily
            )
        }
        assertTrue(tvLarge.headlineLarge.fontSize > tvStandard.headlineLarge.fontSize)
        assertTrue(tvLarge.bodyMedium.fontSize > tvStandard.bodyMedium.fontSize)
    }

    @Test
    fun cinemaHaloTooltipGoldAndDarkOverlayContrastPassWcagAcrossAllTenPresets() {
        val m3DefaultPink = Color(0xFFFFD8E4)
        val cinemaAccentInk = Color(0xFF041724)
        BousygramThemePreset.entries.forEach { preset ->
            val scheme = schemeFor(preset)
            val halo = haloColorsFor(preset, scheme)
            val floor = if (preset == BousygramThemePreset.HIGH_CONTRAST) 7.0 else 4.5

            val tooltipContrast = themeContrastRatio(halo.tooltipContent, halo.tooltipFill)
            assertTrue(
                "${preset.id} tooltip contrast ($tooltipContrast) must be >= $floor",
                tooltipContrast >= floor
            )

            val tooltipIconTint = if (halo.isLight) BousygramUiTokens.mascotCyan else halo.haloStart
            val tooltipIconContrast = themeContrastRatio(tooltipIconTint, halo.tooltipFill)
            assertTrue(
                "${preset.id} tooltip icon contrast ($tooltipIconContrast) must be >= 4.5",
                tooltipIconContrast >= 4.5
            )

            assertNotEquals(
                "${preset.id} goldContainer must not inherit M3 default pink",
                m3DefaultPink,
                halo.goldContainer
            )
            val goldContainerContrast = themeContrastRatio(halo.onGoldContainer, halo.goldContainer)
            assertTrue(
                "${preset.id} gold container contrast ($goldContainerContrast) must be >= $floor",
                goldContainerContrast >= floor
            )

            val goldOnSurfaceContrast = themeContrastRatio(halo.goldAccent, scheme.surface)
            assertTrue(
                "${preset.id} goldAccent on surface ($goldOnSurfaceContrast) must be >= $floor",
                goldOnSurfaceContrast >= floor
            )

            val cinemaAccent = if (halo.isLight) BousygramUiTokens.mascotCyan else halo.haloStart
            val cinemaOnBlack = themeContrastRatio(cinemaAccent, Color.Black)
            val cinemaButtonInk = themeContrastRatio(cinemaAccentInk, cinemaAccent)
            assertTrue(
                "${preset.id} cinema dark overlay accent on black ($cinemaOnBlack) must be >= 7.0",
                cinemaOnBlack >= 7.0
            )
            assertTrue(
                "${preset.id} cinema button ink on accent ($cinemaButtonInk) must be >= 7.0",
                cinemaButtonInk >= 7.0
            )
        }
    }
}
