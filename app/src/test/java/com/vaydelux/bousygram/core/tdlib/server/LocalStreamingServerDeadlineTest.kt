package com.vaydelux.bousygram.core.tdlib.server

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalStreamingServerDeadlineTest {

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
