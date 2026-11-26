package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RollingHlsOwnedWindowDriverTest {
    private val owner = AccountSnapshot(1, 1)
    private var account: AccountSnapshot? = owner
    private var busy = false
    private val gate = RollingHlsDownloadOwnership({ it == account }, { 100L }, { busy })
    private val storage = RollingHlsDownloadOwnership.Storage(0, 0, 1024L * 1024 * 1024)
    private val lock = Mutex()
    private val gateway = Fake()
    private val driver = RollingHlsOwnedWindowDriver(gate, gateway, lock, { storage }, { it == account })

    private inner class Fake : RollingHlsOwnedWindowDriver.Gateway {
        var active = false
        var starts = 0
        var lastLimit = 0
        var returnedId = 7
        var startReturnedId: Int? = null
        var cancelRead = false
        var nullStart = false
        var cancel = false
        var revokeOnRead = false
        var bytes: ByteArray? = byteArrayOf(1, 2)
        override suspend fun status(owner: AccountSnapshot, fileId: Int): RollingHlsOwnedWindowDriver.Status {
            assertTrue(lock.isLocked)
            assertEquals(this@RollingHlsOwnedWindowDriverTest.owner, owner)
            return RollingHlsOwnedWindowDriver.Status(returnedId, 8, active)
        }
        override suspend fun start(owner: AccountSnapshot, fileId: Int, offset: Long, limit: Int):
            RollingHlsOwnedWindowDriver.Status? {
            assertTrue(lock.isLocked)
            assertEquals(this@RollingHlsOwnedWindowDriverTest.owner, owner)
            starts++
            lastLimit = limit
            active = true
            if (cancel) throw CancellationException("lost native callback")
            if (nullStart) return null
            return startReturnedId?.let { RollingHlsOwnedWindowDriver.Status(it, 8, active) }
                ?: status(owner, fileId)
        }
        override suspend fun read(owner: AccountSnapshot, fileId: Int, offset: Long, count: Int): ByteArray? {
            assertTrue(lock.isLocked)
            assertEquals(this@RollingHlsOwnedWindowDriverTest.owner, owner)
            if (cancelRead) throw CancellationException("Interrupted source read")
            if (revokeOnRead) account = null
            return bytes
        }
    }

    private fun lease() = gate.acquire(owner, 7, 8)!!

    @Test fun boundedCommandAndSequentialRead() = runBlocking {
        val ticket = (driver.open(lease(), 0) as RollingHlsOwnedWindowDriver.Open.Ready).window
        assertEquals(8, gateway.lastLimit)
        assertArrayEquals(byteArrayOf(1, 2), driver.read(ticket, 0, 2))
        gateway.bytes = null
        assertNull(driver.read(ticket, 2, 2))
        assertEquals(2L, gate.state().nextOffset)
        assertFalse(driver.reconcile(ticket))
        gateway.active = false
        assertTrue(driver.reconcile(ticket))
    }

    @Test fun existingDownloadAndWrongIdentityDoNotStart() = runBlocking {
        val lease = lease()
        gateway.active = true
        assertEquals(RollingHlsOwnedWindowDriver.Open.Rejected, driver.open(lease, 0))
        gateway.active = false
        gateway.returnedId = 9
        assertEquals(RollingHlsOwnedWindowDriver.Open.Rejected, driver.open(lease, 0))
        assertEquals(0, gateway.starts)
    }

    @Test fun ambiguousStartRetainsReservationEvenIfStatusInactive() = runBlocking {
        val lease = lease()
        gateway.nullStart = true
        val ticket = (driver.open(lease, 0) as RollingHlsOwnedWindowDriver.Open.Uncertain).window
        gateway.active = false
        assertFalse(driver.reconcile(ticket))
        assertEquals(8L, gate.state().reservedBytes)
        assertNull(gate.acquire(owner, 8, 8))
    }

    @Test fun mismatchedStartResponseNeverClearsOutstandingIssue() = runBlocking {
        val lease = lease()
        gateway.startReturnedId = 9
        val ticket = (driver.open(lease, 0) as RollingHlsOwnedWindowDriver.Open.Uncertain).window
        gateway.active = false
        assertFalse(driver.reconcile(ticket))
        assertEquals(8L, gate.state().reservedBytes)
        assertEquals(RollingHlsDownloadOwnership.Phase.QUARANTINED, gate.state().phase)
    }

    @Test fun interruptedReadRetiresLeaseBeforeAnyRetry() = runBlocking {
        val ticket = (driver.open(lease(), 0) as RollingHlsOwnedWindowDriver.Open.Ready).window
        gateway.cancelRead = true
        try {
            driver.read(ticket, 0, 2)
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        gateway.cancelRead = false
        assertFalse(gate.canRead(ticket, 0, 2))
        assertEquals(8L, gate.state().reservedBytes)
        gateway.active = false
        assertTrue(driver.reconcile(ticket))
        assertEquals(0L, gate.state().reservedBytes)
    }

    @Test fun cancellationDuringDispatchDoesNotFreeOwnership() = runBlocking {
        gateway.cancel = true
        val lease = lease()
        try {
            driver.open(lease, 0)
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(RollingHlsDownloadOwnership.Phase.QUARANTINED, gate.state().phase)
        assertEquals(8L, gate.state().reservedBytes)
        account = null
        assertTrue(gate.confirmAccountTeardown(lease, true))
    }

    @Test fun accountRevocationDuringReadWipesReturnedBytes() = runBlocking {
        val ticket = (driver.open(lease(), 0) as RollingHlsOwnedWindowDriver.Open.Ready).window
        gateway.revokeOnRead = true
        try {
            driver.read(ticket, 0, 2)
            fail("Expected revocation")
        } catch (_: IOException) { }
        assertArrayEquals(byteArrayOf(0, 0), gateway.bytes)
        assertEquals(0L, gate.state().nextOffset)
    }

    @Test fun playbackConflictRejectsProducerRead() = runBlocking {
        val ticket = (driver.open(lease(), 0) as RollingHlsOwnedWindowDriver.Open.Ready).window
        busy = true
        try {
            driver.read(ticket, 0, 2)
            fail("Expected conflict")
        } catch (_: IOException) { }
        busy = false
        assertFalse(gate.canRead(ticket, 0, 2))
        assertEquals(0L, gate.state().nextOffset)
    }
}
