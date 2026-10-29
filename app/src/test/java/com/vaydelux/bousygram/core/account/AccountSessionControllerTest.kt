package com.vaydelux.bousygram.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
