package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekPreviewSamplingPolicyTest {
    @Test fun eightPreviewSamplesRemainBounded() {
        val times = (0 until 8).map { seekPreviewSampleTimeUs(80_000L, it, 8) }
        assertEquals(0L, times.first())
        assertEquals(70_000_000L, times.last())
        assertTrue(times.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun malformedMetadataNeverOverflowsTimestamps() {
        val times = (0 until 8).map { seekPreviewSampleTimeUs(Long.MAX_VALUE, it, 8) }
        assertTrue(times.all { it >= 0L })
        assertTrue(times.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(0L, seekPreviewSampleTimeUs(-2L, 0, 8))
        assertEquals(0L, seekPreviewSampleTimeUs(1000L, -1, 8))
        assertEquals(0L, seekPreviewSampleTimeUs(1000L, 8, 8))
        assertEquals(0L, seekPreviewSampleTimeUs(1000L, 0, 0))
    }
}
