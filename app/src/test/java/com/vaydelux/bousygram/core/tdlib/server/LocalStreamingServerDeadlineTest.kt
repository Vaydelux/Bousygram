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
