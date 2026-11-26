package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class RollingLanSessionLifetimeTest {
    @Test fun shortAndLongProfilesRemainSeparate() {
        val short = RollingLanSessionLifetime.QUALIFICATION
        val long = RollingLanSessionLifetime.DIRECT_VIDEO
        assertEquals(180_000L, short.operationMs)
        assertEquals(120_000L, short.playbackMs)
        assertEquals(14_400_000L, long.playbackMs)
        assertTrue(long.operationMs > long.playbackMs)
        assertTrue(long.isActive(3_600_000L, 0L))
        assertFalse(short.isActive(3_600_000L, 0L))
    }
    @Test fun expiryAndClockRollbackFailClosed() {
        val profile = RollingLanSessionLifetime.DIRECT_VIDEO
        val start = 100L
        assertTrue(profile.isActive(start, start))
        assertFalse(profile.isActive(start - 1, start))
        assertTrue(profile.isActive(profile.deadline(start) - 1, start))
        assertFalse(profile.isActive(profile.deadline(start), start))
    }
}
