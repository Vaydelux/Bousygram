package com.vaydelux.bousygram.core.tdlib.client

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NativeRequestCancellationTest {
    @Test fun cancellationAfterRegistrationForgetsNativeHandler() {
        val forgotten = mutableListOf<Long>()
        val registration = NativeRequestCancellation { forgotten += it }
        registration.attach(123L)
        registration.cancel()
        registration.cancel()
        assertEquals(listOf(123L), forgotten)
    }

    @Test fun cancellationBeforeSendReturnsStillForgetsHandler() {
        val forgotten = mutableListOf<Long>()
        val registration = NativeRequestCancellation { forgotten += it }
        registration.cancel()
        registration.attach(456L)
        assertEquals(listOf(456L), forgotten)
    }

    @Test fun nonNativeFallbackHasNoHandlerToForget() {
        val forgotten = mutableListOf<Long>()
        val registration = NativeRequestCancellation { forgotten += it }
        registration.attach(0L)
        registration.cancel()
        assertEquals(emptyList<Long>(), forgotten)
    }

    @Test fun concurrentCancellationAndRegistrationForgetExactlyOnce() {
        repeat(100) {
            val forgotten = Collections.synchronizedList(mutableListOf<Long>())
            val registration = NativeRequestCancellation { forgotten.add(it) }
            val gate = CountDownLatch(1)
            val completed = CountDownLatch(2)
            val register = Thread {
                gate.await()
                registration.attach(999L)
                completed.countDown()
            }
            val cancel = Thread {
                gate.await()
                registration.cancel()
                completed.countDown()
            }
            register.start()
            cancel.start()
            gate.countDown()
            if (!completed.await(2, TimeUnit.SECONDS)) {
                throw AssertionError("Native request registration/cancellation deadlocked")
            }
            assertEquals(listOf(999L), forgotten)
        }
    }
}
