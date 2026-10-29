package com.vaydelux.bousygram.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AccountSessionControllerTest {
    @Test
    fun loggedOutGenerationCannotBecomeTheNextAccount() {
        val session = AccountSessionController()
        val oldGeneration = session.generation()
        val accountA = session.activate(101L, oldGeneration)
        assertNotNull(accountA)
        assertTrue(session.isCurrent(accountA!!))

        val newGeneration = session.invalidate()
        assertFalse(session.isCurrent(accountA))
        assertNull(session.current())
        assertNull(session.activate(202L, oldGeneration))

        val accountB = session.activate(202L, newGeneration)
        assertEquals(202L, accountB?.accountId)
        assertFalse(session.isCurrent(accountA))
    }

    @Test
    fun accountSpecificCallbackCannotCommitAfterLogout() {
        val session = AccountSessionController()
        val accountA = session.activate(101L, session.generation())!!
        var commits = 0

        assertTrue(session.runIfCurrent(accountA) { commits++ })
        session.invalidate()
        assertFalse(session.runIfCurrent(accountA) { commits++ })
        assertEquals(1, commits)
    }

    @Test
    fun everyCleanupRunsAndOldCallbacksCannotCommitAcrossAccounts() {
        val session = AccountSessionController()
        val oldOwner = session.activate(101L, session.generation())!!
        val cleanup = mutableListOf<String>()
        session.registerInvalidationHandler { cleanup += "streams" }
        session.registerInvalidationHandler { cleanup += "storage" }
        val nextGeneration = session.invalidate()

        assertEquals(listOf("streams", "storage"), cleanup)
        assertNull(session.current())
        assertFalse(session.runIfCurrent(oldOwner) { cleanup += "stale callback" })
        val nextOwner = session.activate(202L, nextGeneration)
        assertEquals(202L, nextOwner?.accountId)
        assertEquals(listOf("streams", "storage"), cleanup)
    }

    @Test
    fun failureDoesNotSkipRemainingCleanupAndRetryMayRestoreAccess() {
        val session = AccountSessionController()
        val original = session.activate(101L, session.generation())!!
        var shouldFail = true
        var otherCleanupCalls = 0
        session.registerInvalidationHandler {
            if (shouldFail) throw IllegalStateException("resource still open")
        }
        session.registerInvalidationHandler { otherCleanupCalls++ }

        assertTrue(runCatching { session.invalidate() }.exceptionOrNull() is IllegalStateException)
        assertEquals(1, otherCleanupCalls)
        assertFalse(session.isCurrent(original))
        assertNull(session.activate(202L, session.generation()))

        shouldFail = false
        val generation = session.invalidate()
        assertEquals(2, otherCleanupCalls)
        assertEquals(202L, session.activate(202L, generation)?.accountId)
    }

    @Test
    fun cleanupCanWaitForOtherThreadsWithoutDeadlockingSessionState() {
        val session = AccountSessionController()
        val owner = session.activate(101L, session.generation())!!
        val finished = CountDownLatch(1)
        val acceptedOldAccount = AtomicBoolean(true)
        session.registerInvalidationHandler {
            // Simulates an Android player owner releasing on the main thread. The
            // thread checks account validity while the logout thread waits for it.
            Thread {
                acceptedOldAccount.set(session.runIfCurrent(owner) {})
                finished.countDown()
            }.apply { isDaemon = true; start() }
            assertTrue("Cleanup must not hold the session state lock", finished.await(2, TimeUnit.SECONDS))
        }
        val generation = session.invalidate()
        assertFalse(acceptedOldAccount.get())
        assertNull(session.current())
        assertEquals(202L, session.activate(202L, generation)?.accountId)
    }

    @Test
    fun failedResourceInvalidationBlocksSubsequentAccountActivation() {
        val session = AccountSessionController()
        session.activate(101L, session.generation())
        session.registerInvalidationHandler { throw IllegalStateException("Simulated cleanup failure") }

        try {
            session.invalidate()
            org.junit.Assert.fail("Expected a fail-closed account resource invalidation")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("could not be invalidated"))
        }

        assertNull(session.current())
        assertNull(session.activate(202L, session.generation()))
    }
}
