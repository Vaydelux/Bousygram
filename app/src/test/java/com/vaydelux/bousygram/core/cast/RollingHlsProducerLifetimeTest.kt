package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import org.junit.Assert.*
import org.junit.Test

class RollingHlsProducerLifetimeTest {
    private val account = AccountSnapshot(1, 1)
    private var active = account
    private val gate = RollingHlsProducerLifetime { it == active }

    @Test fun singleGlobalSlotAcrossFileIds() {
        val first = gate.acquire(account, 7)!!
        assertNull(gate.acquire(account, 8))
        assertTrue(gate.beginWorker(first))
        assertFalse(gate.beginWorker(first))
        gate.retire(first)
        assertFalse(gate.permitted(first))
        assertFalse(gate.release(first, 0, true))
        gate.workerExited(first)
        assertTrue(gate.release(first, 0, true))
        assertNotNull(gate.acquire(account, 8))
    }

    @Test fun nativeReservationAndScratchCleanupBothRequired() {
        val ticket = gate.acquire(account, 7)!!
        gate.retire(ticket)
        assertTrue(gate.readyToRelease(ticket))
        assertFalse(gate.release(ticket, 2, true))
        assertFalse(gate.release(ticket, 0, false))
        assertNull(gate.acquire(account, 8))
        assertTrue(gate.release(ticket, 0, true))
    }

    @Test fun logoutDoesNotFreeWorkerOrReservation() {
        val ticket = gate.acquire(account, 7)!!
        assertTrue(gate.beginWorker(ticket))
        active = AccountSnapshot(2, 2)
        assertFalse(gate.permitted(ticket))
        assertNull(gate.acquire(active, 7))
        gate.retire(ticket)
        assertFalse(gate.release(ticket, 0, true))
        gate.workerExited(ticket)
        assertFalse(gate.release(ticket, 1, true))
        assertTrue(gate.release(ticket, 0, true))
        assertNotNull(gate.acquire(active, 7))
    }

    @Test fun staleCleanupCannotReleaseNewSlot() {
        val previous = gate.acquire(account, 7)!!
        gate.retire(previous)
        assertTrue(gate.release(previous, 0, true))
        val next = gate.acquire(account, 7)!!
        gate.workerExited(previous)
        gate.retire(previous)
        assertFalse(gate.release(previous, 0, true))
        assertTrue(gate.permitted(next))
    }
}
