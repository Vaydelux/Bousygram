package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.cache.TvStorageBudget
import org.junit.Assert.*
import org.junit.Test

class RollingHlsDownloadOwnershipTest {
    private val account = AccountSnapshot(11L, 1L)
    private var active: AccountSnapshot? = account
    private var now = 100L
    private var busy = false
    private val gate = RollingHlsDownloadOwnership({ it == active }, { now }, { busy })
    private val storage = RollingHlsDownloadOwnership.Storage(0, 0, 1024L * TvStorageBudget.MIB)

    @Test fun onlyOneProducerAndPositiveClippedWindow() {
        val lease = gate.acquire(account, 8, 3)!!
        assertNull(gate.acquire(account, 9, 100))
        assertNull(gate.reserve(lease, 1, storage))
        val window = gate.reserve(lease, 0, storage)!!
        assertEquals(3, window.limit)
        assertEquals(3L, gate.state().reservedBytes)
        assertTrue(gate.beginIssue(window, storage))
        assertFalse(gate.beginIssue(window, storage))
        assertTrue(gate.acknowledge(window, true))
        assertTrue(gate.consumed(window, 0, 2))
        assertFalse(gate.consumed(window, 0, 1))
        assertFalse(gate.canRead(window, 2, 2))
        assertTrue(gate.consumed(window, 2, 1))
        assertFalse(gate.confirmStopped(window, storage, false))
        assertTrue(gate.confirmStopped(window, storage, true))
        assertNull(gate.reserve(lease, 3, storage))
        gate.retire(lease)
        assertNotNull(gate.acquire(account, 9, 100))
    }

    @Test fun sharedBudgetAndDiskFloorRecheckedBeforeDispatch() {
        val lease = gate.acquire(account, 1, Long.MAX_VALUE)!!
        val tight = storage.copy(tdLibBytes = TvStorageBudget.TOTAL_MEDIA_CACHE_BYTES - 1)
        assertNull(gate.reserve(lease, 0, tight))
        val window = gate.reserve(lease, 0, storage)!!
        assertEquals(RollingHlsDownloadOwnership.MAX_DOWNLOAD_BYTES, window.limit.toLong())
        assertFalse(gate.beginIssue(window, tight))
        assertFalse(gate.beginIssue(window, storage.copy(freeBytes = RollingHlsBudget.MIN_FREE_BYTES)))
        assertEquals(RollingHlsDownloadOwnership.Phase.RESERVED, gate.state().phase)
        gate.retire(lease)
        assertEquals(0L, gate.state().reservedBytes)
    }

    @Test fun expiryAndConflictCannotDispatch() {
        busy = true
        assertNull(gate.acquire(account, 1, 100))
        busy = false
        val lease = gate.acquire(account, 1, 100)!!
        val window = gate.reserve(lease, 0, storage)!!
        busy = true
        assertFalse(gate.beginIssue(window, storage))
        busy = false
        now += RollingHlsDownloadOwnership.WINDOW_DEADLINE_MS
        assertFalse(gate.beginIssue(window, storage))
        gate.retire(lease)
        assertNotNull(gate.acquire(account, 1, 100))
    }

    @Test fun lateAcknowledgementAfterRetirementNeverReleasesReservation() {
        val lease = gate.acquire(account, 1, 100)!!
        val window = gate.reserve(lease, 0, storage)!!
        assertTrue(gate.beginIssue(window, storage))
        gate.retire(lease)
        // A status read before the in-flight dispatch returns is not a stop proof.
        assertFalse(gate.confirmStopped(window, storage, true))
        assertFalse(gate.acknowledge(window, true))
        assertFalse(gate.canRead(window, 0, 1))
        assertEquals(RollingHlsDownloadOwnership.Phase.QUARANTINED, gate.state().phase)
        assertEquals(100L, gate.state().reservedBytes)
        assertNull(gate.acquire(account, 1, 100))
        assertTrue(gate.confirmStopped(window, storage, true))
        val newLease = gate.acquire(account, 1, 100)!!
        gate.retire(lease)
        assertNotNull(gate.reserve(newLease, 0, storage))
        assertFalse(gate.confirmStopped(window, storage, true))
    }

    @Test fun uncertainNativeFailureQuarantinesUntilFreshStopEvidence() {
        val lease = gate.acquire(account, 1, 100)!!
        val window = gate.reserve(lease, 0, storage)!!
        assertTrue(gate.beginIssue(window, storage))
        assertFalse(gate.acknowledge(window, false))
        assertFalse(gate.confirmStopped(window, storage, false))
        assertFalse(gate.confirmStopped(window, storage.copy(appBytes = Long.MAX_VALUE), true))
        assertTrue(gate.confirmStopped(window, storage, true))
        assertNotNull(gate.reserve(lease, 0, storage))
    }

    @Test fun accountChangeFencesOldCommandsAndCleanup() {
        val lease = gate.acquire(account, 1, 100)!!
        val window = gate.reserve(lease, 0, storage)!!
        assertTrue(gate.beginIssue(window, storage))
        active = AccountSnapshot(22, 2)
        assertFalse(gate.acknowledge(window, true))
        assertFalse(gate.confirmStopped(window, storage, true))
        assertNull(gate.acquire(active!!, 1, 100))
        assertFalse(gate.confirmAccountTeardown(lease, false))
        assertTrue(gate.confirmAccountTeardown(lease, true))
        val next = gate.acquire(active!!, 1, 100)!!
        assertFalse(gate.confirmAccountTeardown(lease, true))
        assertNotNull(gate.reserve(next, 0, storage))
    }

    @Test fun playbackTakeoverAndPartialWindowDoNotAdvanceCursor() {
        val lease = gate.acquire(account, 1, 100)!!
        val window = gate.reserve(lease, 0, storage)!!
        assertTrue(gate.beginIssue(window, storage))
        assertTrue(gate.acknowledge(window, true))
        assertTrue(gate.consumed(window, 0, 10))
        busy = true
        assertFalse(gate.consumed(window, 10, 1))
        assertTrue(gate.confirmStopped(window, storage, true))
        assertNull(gate.reserve(lease, 10, storage))
        busy = false
        val resumed = gate.reserve(lease, 10, storage)!!
        assertEquals(90, resumed.limit)
        assertFalse(gate.beginIssue(window, storage))
    }
}
