package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class RollingLanPlaybackProgressTest {
    @Test fun startupAndClockBounded() {
        val progress = RollingLanPlaybackProgress(100L)
        assertNull(progress.observe(30_099L, 0, false, true, false))
        assertEquals(RollingLanPlaybackProgress.Failure.STARTUP,
            progress.observe(30_100L, 0, false, true, false))
        assertEquals(RollingLanPlaybackProgress.Failure.CLOCK,
            progress.observe(30_000L, 0, false, true, false))
    }

    @Test fun pauseAndSeekResetStallDeadline() {
        val progress = RollingLanPlaybackProgress(0)
        assertNull(progress.observe(1_000L, 0, true, true, false))
        assertNull(progress.observe(40_000L, 500, true, true, false))
        assertNull(progress.observe(80_000L, 500, true, false, false))
        assertNull(progress.observe(120_000L, 500, true, false, false))
        assertNull(progress.observe(120_100L, 10_000, true, true, false))
        assertNull(progress.observe(165_099L, 10_000, true, true, false))
        assertEquals(RollingLanPlaybackProgress.Failure.STALL,
            progress.observe(165_100L, 10_000, true, true, false))
    }

    @Test fun pauseBeforeFirstFrameIsNotStartupFailure() {
        val progress = RollingLanPlaybackProgress(0)
        assertNull(progress.observe(60_000L, 0, false, false, false))
        assertNull(progress.observe(61_000L, 0, false, true, false))
        assertNull(progress.observe(70_000L, 0, true, true, false))
        assertNull(progress.observe(200_000L, 0, true, true, true))
    }
}
