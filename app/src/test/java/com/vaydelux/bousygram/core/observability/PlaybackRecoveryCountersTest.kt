package com.vaydelux.bousygram.core.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryCountersTest {
    @Test fun rejectsUnsupportedSeekWithoutRecordingSuccess() {
        val counters = PlaybackRecoveryCounters()
        counters.seekRejected()
        counters.seekRequested()
        assertTrue(counters.seekCompleted(true))
        assertFalse(counters.seekCompleted(true))
        val snapshot = counters.snapshot()
        assertEquals(1, snapshot.rejectedSeekCount)
        assertEquals(1, snapshot.confirmedSeekCount)
        assertEquals(0, snapshot.timedOutSeekCount)
        assertEquals(0, snapshot.unresolvedSeekCount)
    }

    @Test fun timeoutAndDuplicatedConfirmationNeverCountAsSuccessful() {
        val counters = PlaybackRecoveryCounters()
        counters.seekRequested()
        assertFalse(counters.seekCompleted(false))
        assertFalse(counters.seekCompleted(true))
        assertEquals(1, counters.snapshot().timedOutSeekCount)
        assertEquals(0, counters.snapshot().confirmedSeekCount)
        assertEquals(0, counters.snapshot().unresolvedSeekCount)
    }

    @Test fun rapidSeekingAndSessionRetryAccountForSupersededRequests() {
        val counters = PlaybackRecoveryCounters()
        counters.seekRequested()
        counters.seekRequested()
        counters.seekRejected()
        counters.seekRequested()
        counters.abandonSeek()
        counters.abandonSeek()
        val stats = counters.snapshot()
        assertEquals(3, stats.supersededSeekCount)
        assertEquals(1, stats.rejectedSeekCount)
        assertEquals(0, stats.unresolvedSeekCount)
    }

    @Test fun fallbackTracksAttemptsAndActualSuccessSeparately() {
        val counters = PlaybackRecoveryCounters()
        counters.fallbackAttempted()
        counters.fallbackCompleted(false)
        counters.fallbackAttempted()
        counters.fallbackCompleted(true)
        counters.fallbackCompleted(true)
        val stats = counters.snapshot()
        assertEquals(2, stats.fallbackAttemptCount)
        assertEquals(1, stats.fallbackSuccessCount)
    }

    @Test fun onePendingSeekAppearsUnresolvedUntilConfirmedOrAbandoned() {
        val counters = PlaybackRecoveryCounters()
        counters.seekRequested()
        assertEquals(1, counters.snapshot().unresolvedSeekCount)
        counters.abandonSeek()
        assertEquals(0, counters.snapshot().unresolvedSeekCount)
    }
}
