package com.vaydelux.bousygram.core.tdlib.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PendingTdRequestsTest {
    @Test fun aLateCallbackCannotFinishAfterAccountInvalidation() {
        val pending = PendingTdRequests()
        var invalidations = 0
        val request = pending.register(generation = 7L) { invalidations++ }
        assertTrue(pending.isPending(request))

        pending.invalidateAll()

        assertEquals(1, invalidations)
        assertEquals(0, pending.pendingCount())
        assertFalse(pending.finish(request))
        assertFalse(pending.isPending(request))
    }

    @Test fun completedRequestNeverReceivesASecondInvalidation() {
        val pending = PendingTdRequests()
        var invalidations = 0
        val request = pending.register(generation = 3L) { invalidations++ }
        assertTrue(pending.finish(request))
        assertFalse(pending.finish(request))
        pending.invalidateAll()

        assertEquals(0, invalidations)
        assertEquals(0, pending.pendingCount())
    }

    @Test fun removingCancelledContinuationPreventsAnyLateCompletion() {
        val pending = PendingTdRequests()
        val request = pending.register(generation = 1L) { error("Continuation was removed") }
        pending.forget(request)
        pending.invalidateAll()
        assertFalse(pending.finish(request))
        assertEquals(0, pending.pendingCount())
    }

    @Test fun concurrentCompletionAndInvalidationHaveExactlyOneWinner() {
        repeat(50) {
            val pending = PendingTdRequests()
            val invalidations = AtomicInteger()
            val completions = AtomicInteger()
            val ticket = pending.register(generation = 1L) { invalidations.incrementAndGet() }
            val gate = CountDownLatch(1)
            val done = CountDownLatch(2)
            val callback = Thread {
                gate.await()
                if (pending.finish(ticket)) completions.incrementAndGet()
                done.countDown()
            }
            val logout = Thread {
                gate.await()
                pending.invalidateAll()
                done.countDown()
            }
            callback.start()
            logout.start()
            gate.countDown()
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertEquals(1, invalidations.get() + completions.get())
            assertEquals(0, pending.pendingCount())
        }
    }
}
