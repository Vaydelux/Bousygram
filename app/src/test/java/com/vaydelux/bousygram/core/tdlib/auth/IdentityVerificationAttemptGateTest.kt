package com.vaydelux.bousygram.core.tdlib.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IdentityVerificationAttemptGateTest {
    @Test
    fun timedOutNativeGetMeCannotActivateAccountAfterRetry() {
        val gate = IdentityVerificationAttemptGate()
        val previous = gate.begin(4L)!!
        assertFalse(gate.begin(4L) != null)
        assertTrue(gate.expire(previous))

        val current = gate.begin(4L)!!
        assertNotEquals(previous, current)
        assertFalse(gate.acceptResponse(previous))
        assertFalse(gate.finish(previous))
        assertTrue(gate.acceptResponse(current))
        assertFalse(gate.expire(current))
        assertTrue(gate.finish(current))
    }

    @Test
    fun nativeCallbackCanBeConsumedOnlyOnce() {
        val gate = IdentityVerificationAttemptGate()
        val attempt = gate.begin(8L)!!
        assertTrue(gate.acceptResponse(attempt))
        assertFalse(gate.acceptResponse(attempt))
        assertFalse(gate.expire(attempt))
        assertTrue(gate.finish(attempt))
        assertFalse(gate.finish(attempt))
    }

    @Test
    fun logoutAndNativeRestartRejectOldIdentityResponses() {
        val gate = IdentityVerificationAttemptGate()
        val oldAccount = gate.begin(10L)!!
        gate.reset()
        assertFalse(gate.acceptResponse(oldAccount))
        assertFalse(gate.expire(oldAccount))
        val newAccount = gate.begin(11L)
        assertNotNull(newAccount)
        assertEquals(11L, newAccount!!.generation)
        assertTrue(gate.acceptResponse(newAccount))
        assertTrue(gate.finish(newAccount))
    }

    @Test
    fun timeoutAndNativeCallbackRaceNeverBothWin() {
        repeat(150) {
            val gate = IdentityVerificationAttemptGate()
            val attempt = gate.begin(7L)!!
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val won = AtomicInteger()
            val callback = Thread {
                ready.countDown()
                start.await()
                if (gate.acceptResponse(attempt)) won.incrementAndGet()
            }
            val timeout = Thread {
                ready.countDown()
                start.await()
                if (gate.expire(attempt)) won.incrementAndGet()
            }
            callback.start()
            timeout.start()
            assertTrue("Racers should begin", ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            callback.join(2_000L)
            timeout.join(2_000L)
            assertFalse(callback.isAlive)
            assertFalse(timeout.isAlive)
            assertEquals("Only one action can claim the pending GetMe", 1, won.get())
        }
    }
}
