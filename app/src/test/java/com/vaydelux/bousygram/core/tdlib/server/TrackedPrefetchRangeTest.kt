package com.vaydelux.bousygram.core.tdlib.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedPrefetchRangeTest {
    @Test fun exclusiveEndpointDoesNotSuppressAdjacentRequest() {
        val range = trackedPrefetchRange(10L, 5L)
        assertEquals(10L..14L, range)
        assertFalse(15L in range)
        assertTrue(14L in range)
    }

    @Test fun extremeOffsetsAndInvalidLengthsDoNotOverflow() {
        assertEquals((Long.MAX_VALUE - 2L)..(Long.MAX_VALUE - 1L),
            trackedPrefetchRange(Long.MAX_VALUE - 2L, 100L))
        assertTrue(trackedPrefetchRange(Long.MAX_VALUE, 1L).isEmpty())
        assertTrue(trackedPrefetchRange(20L, -1L).isEmpty())
        assertEquals(0L..2L, trackedPrefetchRange(-1L, 3L))
    }
}
