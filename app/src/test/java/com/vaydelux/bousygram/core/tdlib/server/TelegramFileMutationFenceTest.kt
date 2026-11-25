package com.vaydelux.bousygram.core.tdlib.server

import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class TelegramFileMutationFenceTest {
    private val account = AccountSnapshot(1, 1)
    private var current = account
    private val fence = TelegramFileMutationFence { it == current }

    @Test fun playbackRevokesSameFileClaimBeforeDispatch() = runBlocking {
        val claim = fence.acquire(account, 7) { false }!!
        assertTrue(fence.isCurrent(claim))
        assertNull(fence.acquire(account, 7) { false })
        fence.legacy(account, 7) { assertFalse(fence.isCurrent(claim)) }
        val replacement = fence.acquire(account, 7) { false }!!
        fence.release(claim)
        assertTrue(fence.isCurrent(replacement))
    }

    @Test fun sameFileCommandWaitsForProducerCriticalSection() = runBlocking {
        val claim = fence.acquire(account, 7) { false }!!
        val entered = CompletableDeferred<Unit>()
        val command = claim.lock.withLock {
            val pending = async { fence.legacy(account, 7) { entered.complete(Unit) } }
            yield()
            assertFalse(entered.isCompleted)
            pending
        }
        command.await()
        assertTrue(entered.isCompleted)
        assertFalse(fence.isCurrent(claim))
    }

    @Test fun queuedOldAccountCannotDispatchIntoNewAccount() = runBlocking {
        val claim = fence.acquire(account, 7) { false }!!
        var invoked = false
        val pending = claim.lock.withLock {
            val queued = async {
                try { fence.legacy(account, 7) { invoked = true }; false }
                catch (_: CancellationException) { true }
            }
            yield()
            current = AccountSnapshot(2, 2)
            queued
        }
        assertTrue(pending.await())
        assertFalse(invoked)
        assertFalse(fence.isCurrent(claim))
    }

    @Test fun uncertainLegacyCommandBlocksProducerUntilAccountChanges() = runBlocking {
        try {
            fence.legacy(account, 7) { throw CancellationException("lost callback") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertNull(fence.acquire(account, 7) { false })
        // Normal playback is never blocked by producer quarantine.
        assertEquals(42, fence.legacy(account, 7) { 42 })
        current = AccountSnapshot(2, 2)
        assertNotNull(fence.acquire(current, 7) { false })
    }

    @Test fun conflictAndUnrelatedCommandDoNotRevokeClaim() = runBlocking {
        assertNull(fence.acquire(account, 7) { true })
        val claim = fence.acquire(account, 7) { false }!!
        fence.legacy(account, 8) { assertTrue(fence.isCurrent(claim)) }
        assertTrue(fence.isCurrent(claim))
    }
}
