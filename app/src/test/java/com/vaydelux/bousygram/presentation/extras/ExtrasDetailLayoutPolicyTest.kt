package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasDetailLayoutPolicyTest {
    @Test fun scalesAcrossPhoneTabletAndTvDpNotPhysicalResolution() {
        val cases = listOf(
            Triple(320f, 568f, false),
            Triple(360f, 740f, false),
            Triple(844f, 390f, false),
            Triple(768f, 1024f, false),
            Triple(960f, 540f, true),
            Triple(1280f, 720f, true),
            Triple(1920f, 1080f, true)
        )
        for ((width, height, tv) in cases) {
            val s = ExtrasDetailLayoutPolicy.resolve(width, height, tv)
            assertTrue("Unsafe gutter on $width x $height", s.horizontalGutterDp >= 12)
            assertTrue("Too-small button on $width x $height", s.controlHeightDp >= 48)
            assertTrue("Invalid episode layout on $width x $height", s.episodeColumns in 1..2)
            assertTrue("Invalid synopsis on $width x $height", s.synopsisCollapsedLines >= 3)
            assertTrue(s.backdropHeightDp >= 100)
        }
    }

    @Test fun tv540dpRemainsCinematicAndSideBySide() {
        val tv = ExtrasDetailLayoutPolicy.resolve(960f, 540f, true)
        val phone = ExtrasDetailLayoutPolicy.resolve(844f, 390f, false)
        assertTrue(tv.sideBySideProfile)
        assertFalse(tv.compactChrome)
        assertTrue(tv.backdropHeightDp > phone.backdropHeightDp)
        assertFalse(phone.sideBySideProfile)
        assertTrue(phone.compactChrome)
    }

    @Test fun posterAndEpisodesBoundedOnWidestTv() {
        val tv = ExtrasDetailLayoutPolicy.resolve(1920f, 1080f, true)
        assertEquals(2, tv.episodeColumns)
        assertEquals(380, tv.horizontalGutterDp)
        assertTrue(tv.posterWidthDp <= 200)
        assertTrue(1920 - tv.horizontalGutterDp * 2 <=
            ExtrasDetailLayoutPolicy.MAX_READABLE_WIDTH_DP)
    }

    @Test fun scalingLargePhoneFontsNeverMakesControlsSmaller() {
        val normal = ExtrasDetailLayoutPolicy.resolve(720f, 820f, false, 1f)
        val huge = ExtrasDetailLayoutPolicy.resolve(720f, 820f, false, 1.8f)
        assertTrue(normal.sideBySideProfile)
        assertFalse(huge.sideBySideProfile)
        assertTrue(huge.controlHeightDp >= 48)
    }

    @Test fun badDimensionsAndFontScalesHaveSafeFallback() {
        val s = ExtrasDetailLayoutPolicy.resolve(
            Float.NaN, Float.NEGATIVE_INFINITY, false, Float.NaN)
        assertTrue(s.horizontalGutterDp >= 12)
        assertTrue(s.episodeColumns >= 1)
        assertFalse(s.sideBySideProfile)
    }
}
