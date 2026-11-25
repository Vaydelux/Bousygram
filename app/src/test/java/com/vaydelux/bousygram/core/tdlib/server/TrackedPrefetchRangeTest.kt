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
    @org.junit.Test fun progressCannotExceedAdmittedBytesOrWrap() {
        org.junit.Assert.assertEquals(110L, advancePrefetchOffset(100L, 200L, 10, 50))
        org.junit.Assert.assertEquals(105L, advancePrefetchOffset(100L, 200L, 10, 5))
        org.junit.Assert.assertEquals(100L, advancePrefetchOffset(100L, 200L, 10, 0))
        org.junit.Assert.assertEquals(Long.MAX_VALUE,
            advancePrefetchOffset(Long.MAX_VALUE - 2, Long.MAX_VALUE, 20, 20))
    }
    @org.junit.Test fun hugeRelativeTailOffsetsCannotWrapIntoValidByteArrayIndexes() {
        org.junit.Assert.assertNull(savedTailSliceBounds(100L, 64, 100L + (1L shl 32), 8))
        org.junit.Assert.assertNull(savedTailSliceBounds(100L, 64, 164L, 8))
        org.junit.Assert.assertNull(savedTailSliceBounds(100L, 64, 99L, 8))
        org.junit.Assert.assertEquals(2..9, savedTailSliceBounds(100L, 64, 102L, 8))
        org.junit.Assert.assertEquals(60..63, savedTailSliceBounds(100L, 64, 160L, 8))
    }

    @org.junit.Test fun randomizedBoundaryProgressNeverExceedsAdmittedRange() {
        val random = kotlin.random.Random(233)
        repeat(1000) {
            val start = Long.MAX_VALUE - random.nextLong(0, 100000)
            val end = saturatingPrefetchEnd(start, random.nextLong(0, 100000))
            val request = random.nextInt(0, 100000)
            val result = random.nextInt(0, 100000)
            val advanced = advancePrefetchOffset(start, end, request, result)
            org.junit.Assert.assertTrue(advanced in start..end)
            org.junit.Assert.assertEquals(minOf(request.toLong(), result.toLong(), end - start), advanced - start)
        }
    }
}
