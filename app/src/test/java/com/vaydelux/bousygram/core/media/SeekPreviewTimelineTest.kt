package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekPreviewTimelineTest {
    @Test fun partialFrameExtractionNeverReindexesAnOldFrameToLaterTime() {
        val duration = 120_000L
        // Decoder captured 0s and 60s only; other six buckets failed.
        val sampled = listOf(0L, 60_000L)
        assertEquals(0, nearestSeekPreviewIndex(sampled, 2_000L, duration))
        assertEquals(1, nearestSeekPreviewIndex(sampled, 63_000L, duration))
        assertNull(nearestSeekPreviewIndex(sampled, 115_000L, duration))
    }

    @Test fun missingCaptureNearRequestedPositionShowsNoStalePreview() {
        assertNull(nearestSeekPreviewIndex(listOf(0L), 90_000L, 120_000L))
        assertNull(nearestSeekPreviewIndex(emptyList(), 0L, 120_000L))
        assertNull(nearestSeekPreviewIndex(listOf(0L), 0L, 0L))
        assertNull(nearestSeekPreviewIndex(listOf(-12L), 10_000L, 120_000L))
    }

    @Test fun timesAreClampedAndClosestSampleIsSelectedByTimestamp() {
        val duration = 80_000L
        val sampled = listOf(40_000L, 0L, 60_000L)
        assertEquals(1, nearestSeekPreviewIndex(sampled, -500L, duration))
        assertEquals(0, nearestSeekPreviewIndex(sampled, 41_000L, duration))
        assertEquals(2, nearestSeekPreviewIndex(sampled, 63_000L, duration))
    }
}
