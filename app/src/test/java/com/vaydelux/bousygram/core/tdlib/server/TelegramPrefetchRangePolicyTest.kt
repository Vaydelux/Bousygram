package com.vaydelux.bousygram.core.tdlib.server

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPrefetchRangePolicyTest {
    @Test fun typicalCacheAheadOffsetIsUnchanged() {
        val size = 2L * 1024L * 1024L * 1024L
        assertEquals(size / 2, prefetchOffsetForPosition(size, 30_000L, 60_000L))
        assertEquals(0L, prefetchOffsetForPosition(size, -10L, 60_000L))
        assertEquals(size - 1L, prefetchOffsetForPosition(size, 90_000L, 60_000L))
    }

    @Test fun extremeMetadataDoesNotOverflowOrSeekBeforeStart() {
        val size = Long.MAX_VALUE - 9L
        val duration = Long.MAX_VALUE - 3L
        val position = duration / 2L
        val expected = BigInteger.valueOf(size)
            .multiply(BigInteger.valueOf(position))
            .divide(BigInteger.valueOf(duration))
            .longValueExact()
        assertEquals(expected, prefetchOffsetForPosition(size, position, duration))
        assertTrue(prefetchOffsetForPosition(size, position, duration) > 0L)
        assertEquals(size - 1L, prefetchOffsetForPosition(size, Long.MAX_VALUE, duration))
    }

    @Test fun unknownDurationHeuristicSaturatesSafely() {
        assertEquals(512_000L, prefetchOffsetForPosition(10_000_000L, 1_000L, 0L))
        assertEquals(99L, prefetchOffsetForPosition(100L, Long.MAX_VALUE, 0L))
        assertEquals(0L, prefetchOffsetForPosition(0L, Long.MAX_VALUE, 0L))
        assertEquals(0L, prefetchOffsetForPosition(1L, -10L, -1L))
    }

    @Test fun cacheAheadSeekCancellationUsesExclusiveRangeEnd() {
        val window = 100L..200L
        assertTrue(shouldCancelCacheAheadForSeek(0L, 100L, window))
        assertTrue(shouldCancelCacheAheadForSeek(201L, 220L, window))
        assertEquals(false, shouldCancelCacheAheadForSeek(99L, 101L, window))
        assertEquals(false, shouldCancelCacheAheadForSeek(200L, 201L, window))
        assertEquals(false, shouldCancelCacheAheadForSeek(100L, 200L, window))
        assertEquals(false, shouldCancelCacheAheadForSeek(Long.MAX_VALUE - 8L, Long.MAX_VALUE, (Long.MAX_VALUE - 8L)..Long.MAX_VALUE))
    }

    @Test fun rangeEndsAndSeekWindowsCannotWrap() {
        assertEquals(Long.MAX_VALUE, saturatingPrefetchEnd(Long.MAX_VALUE - 8L, 32L))
        assertEquals(128L, saturatingPrefetchEnd(96L, 32L))
        assertEquals(0L, saturatingPrefetchEnd(-20L, -50L))
        val nearMax = prefetchSeekWindow(Long.MAX_VALUE - 4L, 32L * 1024L * 1024L)
        assertEquals(Long.MAX_VALUE, nearMax.last)
        assertTrue(nearMax.first >= 0L)
        assertEquals(0L..0L, prefetchSeekWindow(-1L, -100L))
        assertEquals(0L..(32L * 1024L * 1024L),
            prefetchSeekWindow(0L, 32L * 1024L * 1024L))
    }
}
