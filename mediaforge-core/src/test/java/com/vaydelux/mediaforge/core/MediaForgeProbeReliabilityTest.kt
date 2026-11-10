package com.vaydelux.mediaforge.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No remote media hosts, background providers or Android hardware needed. */
class MediaForgeProbeReliabilityTest {
    private class ProbeCall(
        private val deliverHeaders: Boolean,
        private val unblockRead: CountDownLatch? = null
    ) : Call {
        private val requestValue = Request.Builder()
            .url("https://cdn.example.org/master.m3u8?signature=never-log")
            .build()
        private val cancelled = AtomicBoolean(false)
        private val enqueued = AtomicBoolean(false)
        private var worker: Thread? = null

        override fun request(): Request = requestValue
        override fun execute(): Response =
            throw AssertionError("Probe must not execute synchronously")
        override fun enqueue(responseCallback: Callback) {
            enqueued.set(true)
            if (deliverHeaders) {
                worker = Thread {
                    responseCallback.onResponse(
                        this,
                        Response.Builder()
                            .request(requestValue)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .build()
                    )
                }.apply { isDaemon = true; start() }
            }
        }
        override fun cancel() {
            cancelled.set(true)
            unblockRead?.countDown()
        }
        override fun isExecuted(): Boolean = enqueued.get()
        override fun isCanceled(): Boolean = cancelled.get()
        override fun timeout(): Timeout = Timeout()
        public override fun clone(): Call = ProbeCall(deliverHeaders, unblockRead)
        fun joinWorker() {
            worker?.join(2_000)
            assertFalse("Body reader must exit after cancellation", worker?.isAlive == true)
        }
    }

    @Test fun cancelBeforeResponseHeadersStopsPendingNetworkCall() = runBlocking {
        val call = ProbeCall(deliverHeaders = false)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            readCancellableProbeResponse(call) { it.code }
        }
        assertTrue(call.isExecuted())
        assertFalse(call.isCanceled())
        job.cancelAndJoin()
        assertTrue(call.isCanceled())
    }

    @Test fun cancelAfterHeadersUnblocksStalledReadAndClosesResponse() = runBlocking {
        val reading = CountDownLatch(1)
        val releaseBody = CountDownLatch(1)
        val call = ProbeCall(deliverHeaders = true, unblockRead = releaseBody)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            readCancellableProbeResponse(call) { response ->
                reading.countDown()
                // Represent a streaming response whose body read stalls after
                // status 200; cancelling its socket must interrupt the read.
                check(releaseBody.await(5, TimeUnit.SECONDS))
                response.code
            }
        }
        assertTrue(withContext(Dispatchers.IO) { reading.await(5, TimeUnit.SECONDS) })
        job.cancelAndJoin()
        assertTrue(call.isCanceled())
        call.joinWorker()
    }
}
