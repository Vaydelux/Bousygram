package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasLayoutPolicyTest {
    @Test fun searchAndFiltersNeverTouchOnSupportedViewports() {
        val viewports = listOf(
            320f to 568f, 360f to 740f, 393f to 852f, 428f to 926f,
            640f to 360f, 844f to 390f, 768f to 1024f, 1024f to 768f,
            960f to 540f, 1280f to 720f, 1920f to 1080f
        )
        for ((width, height) in viewports) {
            for (isTv in listOf(false, true)) {
                val spec = ExtrasLayoutPolicy.resolve(width, height, isTv)
                assertTrue("Search-to-filter gap at $width x $height", spec.searchFilterGapDp >= 8)
                assertTrue("Filter-to-results gap at $width x $height", spec.filterResultsGapDp >= 6)
                assertTrue("Minimum controls at $width x $height", spec.minControlHeightDp >= 48)
                assertTrue("Side gutter at $width x $height", spec.horizontalGutterDp >= 12)
                assertTrue("Card gap at $width x $height", spec.gridGapDp >= 10)
            }
        }
    }

    @Test fun smallPhoneGetsTwoUsefulPosterColumnsWithoutCrowdingSearch() {
        val spec = ExtrasLayoutPolicy.resolve(320f, 568f, false)
        val usable = 320 - 2 * spec.horizontalGutterDp
        assertTrue(usable >= 2 * spec.gridCardMinWidthDp + spec.gridGapDp)
        assertEquals(12, spec.horizontalGutterDp)
    }

    @Test fun shortPhoneLandscapeUsesCompactChromeButTvDoesNot() {
        assertTrue(ExtrasLayoutPolicy.resolve(740f, 360f, false).compactHeader)
        assertFalse(ExtrasLayoutPolicy.resolve(960f, 540f, true).compactHeader)
        assertFalse(ExtrasLayoutPolicy.resolve(1280f, 720f, true).compactHeader)
    }

    @Test fun largeFontsUseFewerLegibleCardsAndDoNotChangeTvDensity() {
        val regular = ExtrasLayoutPolicy.resolve(360f, 800f, false, 1f)
        val large = ExtrasLayoutPolicy.resolve(360f, 800f, false, 1.6f)
        assertTrue(large.gridCardMinWidthDp > regular.gridCardMinWidthDp)
        assertEquals(
            ExtrasLayoutPolicy.resolve(960f, 540f, true, 1f).gridCardMinWidthDp,
            ExtrasLayoutPolicy.resolve(960f, 540f, true, 1.9f).gridCardMinWidthDp
        )
    }

    @Test fun invalidDimensionsRemainSafe() {
        val bad = ExtrasLayoutPolicy.resolve(Float.NaN, -20f, false, Float.POSITIVE_INFINITY)
        assertTrue(bad.horizontalGutterDp >= 12)
        assertTrue(bad.minControlHeightDp >= 48)
    }

    @Test fun shortTvDetailKeepsCinematicSizingRatherThanPhoneLandscapeChrome() {
        assertFalse(ExtrasLayoutPolicy.compactDetailViewport(
            isTv = true, isShortWindow = true, effectiveHeightDp = 540f))
        assertFalse(ExtrasLayoutPolicy.compactDetailViewport(
            isTv = true, isShortWindow = false, effectiveHeightDp = 400f))
        assertTrue(ExtrasLayoutPolicy.compactDetailViewport(
            isTv = false, isShortWindow = true, effectiveHeightDp = 390f))
        assertTrue(ExtrasLayoutPolicy.compactDetailViewport(
            isTv = false, isShortWindow = false, effectiveHeightDp = 599f))
        assertFalse(ExtrasLayoutPolicy.compactDetailViewport(
            isTv = false, isShortWindow = false, effectiveHeightDp = 600f))
    }

    @Test fun invalidDetailHeightDoesNotAccidentallyShrinkLayout() {
        assertFalse(ExtrasLayoutPolicy.compactDetailViewport(
            isTv = false, isShortWindow = false, effectiveHeightDp = Float.NaN))
        assertFalse(ExtrasLayoutPolicy.compactDetailViewport(
            isTv = true, isShortWindow = true, effectiveHeightDp = Float.POSITIVE_INFINITY))
    }

    @Test fun shortLandscapePhoneUsesSingleRowChrome() {
        for ((width, height) in listOf(740f to 360f, 844f to 390f, 640f to 360f)) {
            val layout = ExtrasLayoutPolicy.resolve(width, height, false)
            assertTrue("Landscape toolbar at $width x $height", layout.landscapeToolbar)
            assertEquals(48, layout.minControlHeightDp)
            assertEquals(16, layout.horizontalGutterDp)
            assertEquals(6, layout.filterResultsGapDp)
        }
        assertFalse(ExtrasLayoutPolicy.resolve(390f, 844f, false).landscapeToolbar)
        assertFalse(ExtrasLayoutPolicy.resolve(960f, 540f, true).landscapeToolbar)
        assertFalse(ExtrasLayoutPolicy.resolve(1024f, 768f, false).landscapeToolbar)
        assertFalse(ExtrasLayoutPolicy.resolve(320f, 568f, false).landscapeToolbar)
        assertFalse(ExtrasLayoutPolicy.resolve(520f, 330f, false).landscapeToolbar)
        assertFalse(ExtrasLayoutPolicy.resolve(600f, 350f, false).landscapeToolbar)
        assertFalse(ExtrasLayoutPolicy.resolve(844f, 390f, false, 1.6f).landscapeToolbar)
        assertTrue(ExtrasLayoutPolicy.resolve(844f, 390f, false).landscapeToolbar)
    }

    @Test fun largeFontLandscapeKeepsSearchUsable() {
        val layout = ExtrasLayoutPolicy.resolve(640f, 360f, false, 1.6f)
        assertFalse(layout.landscapeToolbar)
        assertTrue(layout.compactHeader)
    }
}
