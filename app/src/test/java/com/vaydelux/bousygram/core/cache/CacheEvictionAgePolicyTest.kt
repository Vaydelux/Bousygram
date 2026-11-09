package com.vaydelux.bousygram.core.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheEvictionAgePolicyTest {
    private val now = 1_700_000_000_000L
    private val grace = 120_000L

    @Test fun watchedVideoCannotEvictImmediatelyAfterPlayback() {
        assertFalse(canEvictCacheByAccessAge(now, now, grace))
        assertFalse(canEvictCacheByAccessAge(now - 119_999L, now, grace))
        assertTrue(canEvictCacheByAccessAge(now - 120_000L, now, grace))
        assertTrue(canEvictCacheByAccessAge(now - 500_000L, now, grace))
    }

    @Test fun missingFutureAndClockSkewAreHandledConservatively() {
        assertTrue(canEvictCacheByAccessAge(null, now, grace))
        assertFalse(canEvictCacheByAccessAge(now + 100L, now, grace))
        assertFalse(canEvictCacheByAccessAge(now + 1_000_000L, now, grace))
        assertTrue(canEvictCacheByAccessAge(now, now, 0L))
    }

    @Test fun noQuotaPressureKeepsWatchedAndUnwatchedMedia() {
        val mib = TvStorageBudget.MIB
        assertFalse(needsCacheEviction(50L * mib, 160L * mib))
        assertFalse(needsCacheEviction(160L * mib, 160L * mib))
        assertTrue(needsCacheEviction(160L * mib + 1L, 160L * mib))
        assertTrue(needsCacheEviction(1L, 0L))
        assertFalse(needsCacheEviction(-1L, 0L))
    }
}
