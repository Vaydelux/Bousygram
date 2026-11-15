package com.vaydelux.bousygram.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheManualPrunePolicyTest {
    @Test fun boundedManualPruneCanRotatePastOneProtectedPage() {
        assertEquals(3, CacheManager.MAX_MANUAL_PRUNE_PASSES)
        assertTrue(shouldContinueManualCachePrune(true, 0))
        assertTrue(shouldContinueManualCachePrune(true, 1))
        // A second empty page indicates no safely reclaimable tracked media
        // in the current bounded inspection sequence; do not spin indefinitely.
        assertFalse(shouldContinueManualCachePrune(true, 2))
        assertFalse(shouldContinueManualCachePrune(false, 0))
    }
}
