package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasLayoutOrientationTest {
    @Test fun landscapePhoneUsesCompactToolbarAtLargeFontScale() {
        val layout = ExtrasLayoutPolicy.resolve(780f, 360f, false, 2f)
        assertTrue(layout.landscapeToolbar)
        assertTrue(layout.compactHeader)
        assertEquals(48, layout.minControlHeightDp)
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
