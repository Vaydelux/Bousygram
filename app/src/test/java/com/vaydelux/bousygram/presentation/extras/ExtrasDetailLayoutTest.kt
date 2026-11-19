package com.vaydelux.bousygram.presentation.extras

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixed, representative dp canvases; no physical-device or pixel-ABI guessing. */
class ExtrasDetailLayoutTest {
    @Test fun shortLandscapePhonesShowMetadataAndActionsWithoutDuplicateHero() {
        for ((width, height) in listOf(
            640.dp to 360.dp, 720.dp to 400.dp, 800.dp to 360.dp,
            915.dp to 412.dp, 960.dp to 440.dp
        )) {
            val spec = resolveExtrasDetailLayout(width, height, isTv = false)
            assertTrue("Landscape phone $width x $height", spec.compactLandscape)
            assertFalse(spec.showBackdrop)
            assertTrue(spec.metadataInline)
            assertTrue(spec.posterWidth <= 100.dp)
            assertTrue(spec.horizontalPadding <= 12.dp)
            assertEquals(3, spec.reviewCollapsedLines)
        }
    }

    @Test fun portraitPhonesKeepCinematicArtWithCompactPosterAndReadingRoom() {
        for ((width, height) in listOf(
            320.dp to 568.dp, 360.dp to 740.dp, 393.dp to 852.dp,
            412.dp to 915.dp, 480.dp to 960.dp
        )) {
            val spec = resolveExtrasDetailLayout(width, height, isTv = false)
            assertFalse(spec.compactLandscape)
            assertTrue(spec.showBackdrop)
            assertTrue(spec.posterWidth <= 110.dp)
            assertTrue(spec.backdropHeight <= 148.dp)
        }
    }

    @Test fun tvAlwaysKeepsRemoteFriendlyHeroAndSeparateLayout() {
        for ((width, height) in listOf(
            854.dp to 480.dp, 960.dp to 540.dp,
            1280.dp to 720.dp, 1920.dp to 1080.dp
        )) {
            val spec = resolveExtrasDetailLayout(width, height, isTv = true)
            assertFalse(spec.compactLandscape)
            assertTrue(spec.showBackdrop)
            assertTrue(spec.metadataInline)
            assertTrue(spec.posterWidth >= 132.dp)
            assertTrue(spec.horizontalPadding >= 24.dp)
        }
    }

    @Test fun accessibilityFontScalingDoesNotForceHugePostersOrHorizontalOverflow() {
        val portrait = resolveExtrasDetailLayout(320.dp, 568.dp, isTv = false, fontScale = 2f)
        assertTrue(portrait.posterWidth <= 100.dp)
        assertFalse(portrait.metadataInline)
        val landscape = resolveExtrasDetailLayout(800.dp, 440.dp, isTv = false, fontScale = 1.6f)
        assertTrue(landscape.compactLandscape)
        assertFalse(landscape.showBackdrop)
    }

    @Test fun tabletProvidesWiderCardsWithoutTakingPhoneNavigationRules() {
        val spec = resolveExtrasDetailLayout(840.dp, 1180.dp, isTv = false)
        assertFalse(spec.compactLandscape)
        assertTrue(spec.metadataInline)
        assertEquals(150.dp, spec.posterWidth)
        assertTrue(spec.showBackdrop)
    }
}
