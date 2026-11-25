package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RollingHlsOwnedSourceTest {
    private val account = AccountSnapshot(1, 1)
    private var allowed = true
    private val windowBytes = RollingHlsDownloadOwnership.MAX_DOWNLOAD_BYTES
    private val size = windowBytes + 17
    private val gate = RollingHlsDownloadOwnership({ it == account }, { 100L }, { !allowed })
    private val lease = gate.acquire(account, 7, size)!!
    private val gateway = Fake()
    private val storage = RollingHlsDownloadOwnership.Storage(0, 0, 1024L * 1024 * 1024)
    private val driver = RollingHlsOwnedWindowDriver(gate, gateway, Mutex(), { storage }, { it == account })
    private var waits = 0

    private inner class Fake : RollingHlsOwnedWindowDriver.Gateway {
        val requests = mutableListOf<Pair<Long, Int>>()
        var active = false
        var end = 0L
        var pending = 0
        var remainActive = false
        var uncertain = false
        override suspend fun status(owner: AccountSnapshot, fileId: Int) =
            RollingHlsOwnedWindowDriver.Status(fileId, size, active)
        override suspend fun start(owner: AccountSnapshot, fileId: Int, offset: Long, limit: Int):
            RollingHlsOwnedWindowDriver.Status? {
            requests.add(offset to limit)
            end = offset + limit
            active = true
            return if (uncertain) null else status(owner, fileId)
        }
        override suspend fun read(owner: AccountSnapshot, fileId: Int, offset: Long, count: Int): ByteArray? {
            if (pending > 0) { pending--; return null }
            check(offset + count <= end)
            if (offset + count == end && !remainActive) active = false
            return ByteArray(count) { ((offset + it) % 251).toByte() }
        }
    }
    private fun source() = RollingHlsOwnedSource(driver, lease, { allowed }, { waits++ }).source()

    @Test fun renewsOnlyAfterReconciliationAndClipsFinalRead() = runBlocking {
        val source = source()
        var offset = 0L
        while (offset < size) {
            val bytes = (source.readAt(offset, 256 * 1024) as RollingHlsSource.Read.Ready).bytes
            assertTrue(bytes.size <= 32 * 1024)
            assertEquals((offset % 251).toByte(), bytes.first())
            offset += bytes.size
        }
        assertEquals(size, offset)
        assertEquals(listOf(0L to windowBytes.toInt(), windowBytes to 17), gateway.requests)
        assertEquals(RollingHlsSource.Read.End, source.readAt(size, 1))
        val ticket = gate.pendingWindow(lease)!!
        assertTrue(driver.reconcile(ticket))
        assertEquals(0L, gate.state().reservedBytes)
        source.close()
    }

    @Test fun pendingRetriesAreBoundedAndDoNotMoveCursor() = runBlocking {
        val source = source()
        gateway.pending = 6
        assertEquals(RollingHlsSource.Read.Pending, source.readAt(0, 8))
        assertEquals(5, waits)
        assertEquals(0L, gate.state().nextOffset)
        assertEquals(1, gateway.requests.size)
        val bytes = (source.readAt(0, 8) as RollingHlsSource.Read.Ready).bytes
        assertEquals(8, bytes.size)
        source.close()
    }

    @Test fun activePreviousWindowPreventsNewCommand() = runBlocking {
        gateway.remainActive = true
        val source = source()
        var offset = 0L
        while (offset < windowBytes) {
            offset += (source.readAt(offset, 32 * 1024) as RollingHlsSource.Read.Ready).bytes.size
        }
        try { source.readAt(offset, 1); fail("Expected unreconciled window refusal") }
        catch (_: IOException) { }
        assertEquals(1, gateway.requests.size)
        assertEquals(windowBytes, gate.state().reservedBytes)
        assertNotNull(gate.pendingWindow(lease))
        source.close()
    }

    @Test fun ambiguousOpenRetainsRecoverableTicket() = runBlocking {
        gateway.uncertain = true
        val source = source()
        try { source.readAt(0, 1); fail("Expected uncertain command refusal") }
        catch (_: IOException) { }
        assertEquals(RollingHlsDownloadOwnership.Phase.QUARANTINED, gate.state().phase)
        val ticket = gate.pendingWindow(lease)!!
        gateway.active = false
        assertFalse(driver.reconcile(ticket))
        assertEquals(windowBytes, gate.state().reservedBytes)
        source.close()
    }

    @Test fun nonSequentialReadDoesNotIssueCommand() = runBlocking {
        val source = source()
        try { source.readAt(1, 1); fail("Expected nonsequential refusal") }
        catch (_: IOException) { }
        assertTrue(gateway.requests.isEmpty())
        source.close()
    }
}
