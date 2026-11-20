package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasLayoutOrientationTest {
    @Test fun landscapePhoneStacksTabsAtLargeFontScale() {
        val layout = ExtrasLayoutPolicy.resolve(780f, 360f, false, 2f)
        assertFalse(layout.landscapeToolbar)
        assertTrue(layout.compactHeader)
        assertEquals(48, layout.minControlHeightDp)
    }

    @Test fun mediumLandscapeKeepsSearchAndFiltersStacked() {
        val layout = ExtrasLayoutPolicy.resolve(700f, 390f, false)
        assertFalse(layout.landscapeToolbar)
        assertTrue(layout.compactHeader)
    }

    @Test fun minimumPhoneWidthRetainsReadableGridAndGutters() {
        val layout = ExtrasLayoutPolicy.resolve(320f, 568f, false)
        assertFalse(layout.landscapeToolbar)
        assertEquals(12, layout.horizontalGutterDp)
        assertTrue(layout.gridCardMinWidthDp <= 144)
    }

    @Test fun compactPhoneLandscapeDoesNotUseWideToolbar() {
        val layout = ExtrasLayoutPolicy.resolve(640f, 360f, false)
        assertFalse(layout.landscapeToolbar)
        assertTrue(layout.compactHeader)
    }

    @Test fun wideLandscapeAtLargeFontScaleCanFitToolbar() {
        val layout = ExtrasLayoutPolicy.resolve(960f, 480f, false, 1.5f)
        assertTrue(layout.landscapeToolbar)
        assertTrue(layout.gridCardMinWidthDp >= 160)
    }

    @Test fun fontScaleDoesNotShrinkAvailableGridWidth() {
        val normal = ExtrasLayoutPolicy.resolve(400f, 800f, false, 1f)
        val enlarged = ExtrasLayoutPolicy.resolve(400f, 800f, false, 2f)
        assertTrue(enlarged.gridCardMinWidthDp >= normal.gridCardMinWidthDp)
        assertEquals(normal.horizontalGutterDp, enlarged.horizontalGutterDp)
    }

    @Test fun portraitPhoneDoesNotUseLandscapeToolbar() {
        val layout = ExtrasLayoutPolicy.resolve(360f, 780f, false, 1.5f)
        assertFalse(layout.landscapeToolbar)
        assertFalse(layout.compactHeader)
    }

    @Test fun shortTabletLandscapeUsesCompactToolbar() {
        assertTrue(ExtrasLayoutPolicy.resolve(900f, 550f, false).landscapeToolbar)
    }

    @Test fun shortTvNeverUsesPhoneToolbar() {
        val layout = ExtrasLayoutPolicy.resolve(960f, 540f, true)
        assertFalse(layout.landscapeToolbar)
        assertFalse(layout.compactHeader)
        assertEquals(56, layout.minControlHeightDp)
    }

    @Test fun invalidMetricsDoNotCrashPolicy() {
        val layout = ExtrasLayoutPolicy.resolve(Float.NaN, Float.POSITIVE_INFINITY, false)
        assertTrue(layout.gridCardMinWidthDp > 0)
    }
}
