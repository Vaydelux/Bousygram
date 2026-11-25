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
