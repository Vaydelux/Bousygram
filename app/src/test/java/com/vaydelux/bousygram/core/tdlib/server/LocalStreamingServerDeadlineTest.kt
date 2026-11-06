package com.vaydelux.bousygram.core.tdlib.server

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalStreamingServerDeadlineTest {

    @Test
    fun loopbackChunkWritesStayInsideRequestedHttpRange() {
        assertEquals(512, boundedHttpRangeWriteBytes(4096, 1024, 512L))
        assertEquals(1024, boundedHttpRangeWriteBytes(4096, 1024, 5000L))
        assertEquals(128, boundedHttpRangeWriteBytes(128, 1024, 5000L))
        assertEquals(0, boundedHttpRangeWriteBytes(1024, 1024, 0L))
        assertEquals(0, boundedHttpRangeWriteBytes(1024, -1, 5000L))
        assertEquals(1024, boundedHttpRangeWriteBytes(1024, 1024, Long.MAX_VALUE))
    }

    @Test
    fun evictionKeepsNewestFileStatusesAndUsesStableTieBreak() {
        val statuses = mapOf(7 to 100L, 3 to 200L, 5 to 200L, 9 to 300L)
        assertEquals(listOf(7, 3), staleStatusCacheIds(statuses, retainCount = 2))
        assertEquals(emptyList<Int>(), staleStatusCacheIds(statuses, retainCount = 4))
        assertEquals(listOf(7, 3, 5, 9), staleStatusCacheIds(statuses, retainCount = 0))
    }

    @Test
    fun prefetchWindowMatchesNormalVideoRunwayWithoutOverfetch() {
        val size = 200L * 1024L * 1024L
        val w = requireNotNull(calculateSeekPrefetchWindow(size, 100_000L, 200_000L))
        assertEquals(size / 2L, w.offset)
        assertEquals(25L * 1024L * 1024L, w.length)
        assertEquals(null, calculateSeekPrefetchWindow(0L, 60_000L, 120_000L))
    }

    @Test
    fun largeAndUntrustedTimestampsNeverOverflowOrRequestPastFileEnd() {
        for (size in listOf(1L, 100L * 1024L * 1024L, Long.MAX_VALUE)) {
            for (time in listOf(Long.MIN_VALUE, -1L, 0L, 1_000L, Long.MAX_VALUE)) {
                for (duration in listOf(-1L, 0L, 1L, 100_000L, Long.MAX_VALUE)) {
                    val w = requireNotNull(calculateSeekPrefetchWindow(size, time, duration))
                    org.junit.Assert.assertTrue(w.offset in 0 until size)
                    org.junit.Assert.assertTrue(w.length > 0L)
                    org.junit.Assert.assertTrue(w.length <= 48L * 1024L * 1024L)
                    org.junit.Assert.assertTrue(w.length <= size - w.offset)
                }
            }
        }
    }

    @Test
    fun seekPrefetchAtEndAndUnknownDurationHaveConservativeBounds() {
        val size = 100L * 1024L * 1024L
        val atEnd = requireNotNull(calculateSeekPrefetchWindow(size, 120_000L, 120_000L))
        assertEquals(size - 1L, atEnd.offset)
        assertEquals(1L, atEnd.length)

        val noDuration = requireNotNull(calculateSeekPrefetchWindow(size, 1_000_000L, 0L))
        assertEquals(size - 1L, noDuration.offset)
        assertEquals(1L, noDuration.length)

        val atStart = requireNotNull(calculateSeekPrefetchWindow(size, -10_000L, 0L))
        assertEquals(0L, atStart.offset)
        assertEquals(48L * 1024L * 1024L, atStart.length)
    }

    @Test
    fun remainingWaitMs_clampsToRemainingTimeWhenBelowCap() {
        val now = 1_000L
        val deadline = 1_200L // 200ms remaining
        val cap = 500L
        val remaining = LocalStreamingServer.remainingWaitMs(deadline, cap, nowMs = now)
        assertEquals(200L, remaining)
    }

    @Test
    fun remainingWaitMs_clampsToCapWhenRemainingExceedsCap() {
        val now = 1_000L
        val deadline = 2_000L // 1000ms remaining
        val cap = 300L
        val remaining = LocalStreamingServer.remainingWaitMs(deadline, cap, nowMs = now)
        assertEquals(300L, remaining)
    }

    @Test
    fun remainingWaitMs_returnsZeroWhenPastDeadline() {
        val now = 1_500L
        val deadline = 1_200L // 300ms in the past
        val cap = 500L
        val remaining = LocalStreamingServer.remainingWaitMs(deadline, cap, nowMs = now)
        assertEquals(0L, remaining)
    }

    @Test
    fun remainingWaitMs_returnsZeroWhenAtDeadline() {
        val now = 1_000L
        val deadline = 1_000L
        val cap = 500L
        val remaining = LocalStreamingServer.remainingWaitMs(deadline, cap, nowMs = now)
        assertEquals(0L, remaining)
    }
}
