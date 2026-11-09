package com.vaydelux.bousygram.core.media

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlayerThreadDispatchPolicyTest {
    @Test fun delayedQueuedMainThreadActionIsCancelledAfterOwnerTimeout() {
        val queued = AtomicReference<Runnable?>()
        val applied = AtomicInteger()
        assertThrows(TimeoutException::class.java) {
            dispatchPlayerThreadAction(
                enqueue = { task -> queued.set(task); true },
                action = { applied.incrementAndGet() },
                timeoutMs = 1L
            )
        }
        // The handler finally resumes after the owner rejected the handoff.
        queued.get()!!.run()
        assertEquals(0, applied.get())
    }

    @Test fun successfulPlayerThreadActionRunsExactlyOnce() {
        val executed = AtomicInteger()
        dispatchPlayerThreadAction(
            enqueue = { task -> task.run(); true },
            action = { executed.incrementAndGet() },
            timeoutMs = 1_000L
        )
        assertEquals(1, executed.get())
    }

    @Test fun rejectedHandlerDoesNotExecuteCallerAction() {
        val executed = AtomicInteger()
        assertThrows(IllegalStateException::class.java) {
            dispatchPlayerThreadAction(
                enqueue = { false },
                action = { executed.incrementAndGet() },
                timeoutMs = 1_000L
            )
        }
        assertEquals(0, executed.get())
    }

    @Test fun invalidTimeoutIsRejectedBeforeScheduling() {
        assertThrows(IllegalArgumentException::class.java) {
            dispatchPlayerThreadAction(
                enqueue = { throw AssertionError("should not enqueue") },
                action = {},
                timeoutMs = 0L
            )
        }
    }
}
