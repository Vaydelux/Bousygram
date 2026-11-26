package com.vaydelux.bousygram.core.tdlib.server

import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class TelegramFileMaintenanceFenceTest {
    private val account = AccountSnapshot(1, 1)
    private val fence = TelegramFileMutationFence { it == account }

    @Test fun deletionYieldsToProducerAndBusyStripe() = runBlocking {
        val claim = fence.acquire(account, 7) { false }!!
        var calls = 0
        assertNull(fence.maintenance(account, 7, { false }) { ++calls })
        claim.lock.withLock {
            assertNull(fence.maintenance(account, 71, { false }) { ++calls })
        }
        assertEquals(0, calls)
        fence.release(claim)
        assertEquals(1, fence.maintenance(account, 7, { false }) { ++calls })
    }

    @Test fun deletionRechecksProtectionAndOwner() = runBlocking {
        var calls = 0
        assertNull(fence.maintenance(account, 7, { true }) { ++calls })
        assertNull(fence.maintenance(AccountSnapshot(2, 2), 7, { false }) { ++calls })
        assertEquals(0, calls)
    }

    @Test fun uncertainDeletionQuarantinesProducerButNotPlayback() = runBlocking {
        try {
            fence.maintenance(account, 7, { false }) { throw CancellationException("lost reply") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertNull(fence.acquire(account, 7) { false })
        assertEquals(2, fence.legacy(account, 7) { 2 })
    }
}
