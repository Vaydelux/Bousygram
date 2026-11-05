package com.vaydelux.bousygram.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterImageMemoryPolicyTest {
    @Test fun arm32UsesLessThanTenPercentOfHeapForArtwork() {
        assertTrue(decodedPosterCacheHeapFraction(true) < 0.10)
        assertEquals(0.08, decodedPosterCacheHeapFraction(true), 0.0)
    }

    @Test fun modernDevicesKeepBoundedWarmArtworkCache() {
        assertEquals(0.16, decodedPosterCacheHeapFraction(false), 0.0)
        assertTrue(decodedPosterCacheHeapFraction(false) < 0.25)
    }
}
