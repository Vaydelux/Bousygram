package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasImagePolicyTest {
    @Test fun posterFramesKeepProviderArtworkAtTwoByThree() {
        assertEquals(2f / 3f, ExtrasImagePolicy.POSTER_ASPECT_RATIO, 0.0001f)
    }

    @Test fun providerBackdropDecodingIsBoundedOnArm32() {
        val lowMemory = ExtrasImagePolicy.backdrop(lowMemory = true)
        val standard = ExtrasImagePolicy.backdrop(lowMemory = false)
        assertEquals(640, lowMemory.width)
        assertEquals(360, lowMemory.height)
        assertTrue(lowMemory.maximumArgbBytes < 1_000_000L)
        assertEquals(1280, standard.width)
        assertEquals(720, standard.height)
        assertTrue(standard.maximumArgbBytes < 4_000_000L)
    }

    @Test fun arm32OrLowRamPosterDecoderIsMemoryBounded() {
        val grid = ExtrasImagePolicy.poster(lowMemory = true, detail = false)
        val detail = ExtrasImagePolicy.poster(lowMemory = true, detail = true)
        assertEquals(240, grid.width)
        assertEquals(360, grid.height)
        assertTrue(grid.maximumArgbBytes <= 360_000L)
        assertTrue(detail.maximumArgbBytes <= 800_000L)
    }

    @Test fun searchSuggestionsUseOnlyTinyDecodedThumbnails() {
        val small = ExtrasImagePolicy.poster(
            lowMemory = true, detail = false, suggestion = true
        )
        val standard = ExtrasImagePolicy.poster(
            lowMemory = false, detail = false, suggestion = true
        )
        assertEquals(64, small.width)
        assertEquals(96, small.height)
        assertTrue(small.maximumArgbBytes <= 25_000L)
        assertEquals(96, standard.width)
        assertEquals(144, standard.height)
        assertTrue(standard.maximumArgbBytes <= 56_000L)
    }

    @Test fun standardDevicesStillUseBoundedDecodeTargets() {
        val grid = ExtrasImagePolicy.poster(lowMemory = false, detail = false)
        val detail = ExtrasImagePolicy.poster(lowMemory = false, detail = true)
        assertTrue(grid.maximumArgbBytes < 1_000_000L)
        assertTrue(detail.maximumArgbBytes < 2_500_000L)
        assertTrue(grid.width < detail.width)
        assertTrue(grid.height < detail.height)
    }
}
