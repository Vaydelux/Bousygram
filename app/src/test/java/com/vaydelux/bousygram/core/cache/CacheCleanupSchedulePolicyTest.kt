package com.vaydelux.bousygram.core.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheCleanupSchedulePolicyTest {
    @Test fun regularPlaybackDoesNotQueueFullSweepsOnEveryAccess() {
        val level = StoragePressurePolicy.Level.NORMAL
        assertTrue(shouldRequestCacheCleanupForAccess(null, 20_000, level))
        assertFalse(shouldRequestCacheCleanupForAccess(20_000, 20_001, level))
        assertFalse(shouldRequestCacheCleanupForAccess(20_000, 79_999, level))
        assertTrue(shouldRequestCacheCleanupForAccess(20_000, 80_000, level))
    }

    @Test fun lowSpaceMaintainsFasterButStillBoundedCleanupCadence() {
        val low = StoragePressurePolicy.Level.LOW
        val critical = StoragePressurePolicy.Level.CRITICAL
        assertFalse(shouldRequestCacheCleanupForAccess(10_000, 24_999, low))
        assertTrue(shouldRequestCacheCleanupForAccess(10_000, 25_000, low))
        assertFalse(shouldRequestCacheCleanupForAccess(10_000, 14_999, critical))
        assertTrue(shouldRequestCacheCleanupForAccess(10_000, 15_000, critical))
    }

    @Test fun elapsedClockResetAndAccountChangeCanRequestFreshSweep() {
        assertTrue(shouldRequestCacheCleanupForAccess(90_000, 1_000,
            StoragePressurePolicy.Level.NORMAL))
        assertTrue(shouldRequestCacheCleanupForAccess(null, 1_000,
            StoragePressurePolicy.Level.NORMAL))
    }

    @Test fun pressureChangeShortensNextEligibleInterval() {
        assertFalse(shouldRequestCacheCleanupForAccess(40_000, 56_000,
            StoragePressurePolicy.Level.NORMAL))
        assertTrue(shouldRequestCacheCleanupForAccess(40_000, 56_000,
            StoragePressurePolicy.Level.LOW))
    }
}
