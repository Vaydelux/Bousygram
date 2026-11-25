package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class RemuxTimelineTest {
    @Test fun negativeAudioPrimingAndInterleavedOrderPreserveTrackOffsets() {
        val timeline = RemuxTimeline(2, 60_000_000L, 20_000)
        assertTrue(timeline.observe(0, 0L))
        assertTrue(timeline.observe(1, -46_440L))
        assertTrue(timeline.observe(0, 83_333L))
        assertTrue(timeline.observe(1, 0L))
        assertEquals(-46_440L, timeline.origin())
        assertEquals(46_440L, RemuxTimeline.normalize(0, timeline.origin()!!, 60_000_000))
        assertEquals(0L, RemuxTimeline.normalize(-46_440, timeline.origin()!!, 60_000_000))
        assertTrue(timeline.matches(intArrayOf(2, 2)))
        assertFalse(timeline.matches(intArrayOf(2, 1)))
    }

    @Test fun refusesReorderedTrackAndMissingTrack() {
        val timeline = RemuxTimeline(2, 100L, 10)
        assertTrue(timeline.observe(0, 80))
        assertNull(timeline.origin())
        assertFalse(timeline.observe(0, 79))
        assertNull(timeline.origin())
    }

    @Test fun finalDurationAndOutputConservation() {
        val input = RemuxTimeline(2, 1_000_000, 20)
        assertTrue(input.observe(0, 0))
        assertTrue(input.observe(1, -20_000))
        assertTrue(input.observe(0, 80_000))
        assertTrue(input.observe(1, 20_000))
        assertEquals(180_000L, input.endOfTrackUs(0))
        assertEquals(80_000L, input.endOfTrackUs(1))
        val shifted = RemuxTimeline(2, 1_000_000, 20)
        shifted.observe(0, 25_000); shifted.observe(1, 5_000)
        shifted.observe(0, 105_000); shifted.observe(1, 45_000)
        assertTrue(input.preserves(shifted))
        val lost = RemuxTimeline(2, 1_000_000, 20)
        lost.observe(0, 25_000); lost.observe(1, 5_000); lost.observe(1, 45_000)
        assertFalse(input.preserves(lost))
        assertNull(lost.endOfTrackUs(0))
        val drift = RemuxTimeline(2, 1_000_000, 20)
        drift.observe(0, 25_000); drift.observe(1, 5_000)
        drift.observe(0, 105_000); drift.observe(1, 50_000)
        assertFalse(input.preserves(drift))
    }

    @Test fun boundsTimelineSpanAndArithmetic() {
        val timeline = RemuxTimeline(2, 100L, 2)
        assertTrue(timeline.observe(0, -50))
        assertFalse(timeline.observe(1, 51))
        assertNull(timeline.origin())
        assertNull(RemuxTimeline.normalize(Long.MAX_VALUE, -1L, Long.MAX_VALUE))
        assertNull(RemuxTimeline.normalize(1, 2, 100))
        val limit = RemuxTimeline(1, 100, 1)
        assertTrue(limit.observe(0, 1))
        assertFalse(limit.observe(0, 2))
        assertNull(limit.origin())
    }
}
