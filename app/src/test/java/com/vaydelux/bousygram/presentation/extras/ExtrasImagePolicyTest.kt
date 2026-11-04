package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasImagePolicyTest {
    @Test fun arm32OrLowRamPosterDecoderIsMemoryBounded() {
        val grid = ExtrasImagePolicy.poster(lowMemory = true, detail = false)
        val detail = ExtrasImagePolicy.poster(lowMemory = true, detail = true)
        assertEquals(240, grid.width)
        assertEquals(360, grid.height)
        assertTrue(grid.maximumArgbBytes <= 360_000L)
        assertTrue(detail.maximumArgbBytes <= 800_000L)
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
