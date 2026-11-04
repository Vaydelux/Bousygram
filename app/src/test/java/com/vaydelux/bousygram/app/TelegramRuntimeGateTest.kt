package com.vaydelux.bousygram.app

import com.vaydelux.bousygram.presentation.shell.BousygramMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramRuntimeGateTest {
    @Test fun extrasAndModeChooserCannotStartTelegramRuntime() {
        assertFalse(TelegramRuntimePolicy.needsTelegramRuntime(BousygramMode.SELECT))
        assertFalse(TelegramRuntimePolicy.needsTelegramRuntime(BousygramMode.EXTRAS_MAINTENANCE))
        assertTrue(TelegramRuntimePolicy.needsTelegramRuntime(BousygramMode.TELEGRAM))
    }

    @Test fun incomingCastAlwaysRoutesToTelegramNotExtras() {
        assertEquals(
            BousygramMode.TELEGRAM,
            TelegramRuntimePolicy.routeForPendingCast(BousygramMode.EXTRAS_MAINTENANCE, true)
        )
        assertEquals(
            BousygramMode.TELEGRAM,
            TelegramRuntimePolicy.routeForPendingCast(BousygramMode.SELECT, true)
        )
        assertEquals(
            BousygramMode.EXTRAS_MAINTENANCE,
            TelegramRuntimePolicy.routeForPendingCast(BousygramMode.EXTRAS_MAINTENANCE, false)
        )
    }

    @Test fun aSuccessfulStartupIsRunExactlyOnceOnRepeatEntry() {
        val count = AtomicInteger()
        val gate = TelegramRuntimeGate { count.incrementAndGet() }
        assertFalse(gate.isReady)
        gate.ensureStarted()
        gate.ensureStarted()
        assertTrue(gate.isReady)
        assertEquals(1, count.get())
    }

    @Test fun failedInitializationDoesNotPublishReadyAndCanBeRetried() {
        val count = AtomicInteger()
        val gate = TelegramRuntimeGate {
            if (count.incrementAndGet() == 1) throw IllegalStateException("Synthetic initialization failure")
        }
        try {
            gate.ensureStarted()
            throw AssertionError("First startup should fail")
        } catch (_: IllegalStateException) {
            assertFalse(gate.isReady)
        }
        gate.ensureStarted()
        assertTrue(gate.isReady)
        assertEquals(2, count.get())
    }

    @Test fun concurrentEntriesCannotDoubleInitializeNativeServices() {
        val count = AtomicInteger()
        val gate = TelegramRuntimeGate { count.incrementAndGet() }
        val start = CountDownLatch(1)
        val finished = CountDownLatch(12)
        repeat(12) {
            Thread {
                start.await()
                gate.ensureStarted()
                finished.countDown()
            }.start()
        }
        start.countDown()
        assertTrue("Startup tasks did not finish", finished.await(5, TimeUnit.SECONDS))
        assertTrue(gate.isReady)
        assertEquals(1, count.get())
    }
}
