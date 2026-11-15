package com.vaydelux.bousygram.presentation.extras

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasEndpointProbeTest {
    @Test fun statusClassificationNeverEquatesHttpErrorWithDeadDomain() {
        val fixture = mapOf(
            200 to ExtrasEndpointObservation.RESPONDED,
            206 to ExtrasEndpointObservation.RESPONDED,
            302 to ExtrasEndpointObservation.REDIRECTED,
            401 to ExtrasEndpointObservation.RESTRICTED,
            403 to ExtrasEndpointObservation.RESTRICTED,
            404 to ExtrasEndpointObservation.MISSING_ROUTE,
            410 to ExtrasEndpointObservation.MISSING_ROUTE,
            405 to ExtrasEndpointObservation.HEAD_NOT_SUPPORTED,
            429 to ExtrasEndpointObservation.RATE_LIMITED,
            500 to ExtrasEndpointObservation.SERVER_ERROR,
            503 to ExtrasEndpointObservation.SERVER_ERROR,
            102 to ExtrasEndpointObservation.INCONCLUSIVE
        )
        fixture.forEach { (code, expected) ->
            assertEquals("Response $code", expected, classifyExtrasEndpointResponse(code))
        }
        assertTrue(describeExtrasEndpointObservation(ExtrasEndpointObservation.RESPONDED)
            .contains("not verified"))
        assertTrue(describeExtrasEndpointObservation(ExtrasEndpointObservation.RESTRICTED)
            .contains("does not prove"))
    }

    @Test fun checkUsesHeadNoBodyNoRedirectOrCookies() = runBlocking {
        val closed = AtomicBoolean(false)
        val fake = object : HttpURLConnection(URL("https://example.invalid/embed/550")) {
            override fun connect() {}
            override fun disconnect() { closed.set(true) }
            override fun usingProxy() = false
            override fun getResponseCode() = 200
            override fun getInputStream(): java.io.InputStream =
                throw AssertionError("Probing may never download a body")
        }
        val result = ExtrasEndpointProbe { fake }.check("https://example.invalid/embed/550")
        assertEquals(ExtrasEndpointObservation.RESPONDED, result)
        assertEquals("HEAD", fake.requestMethod)
        assertFalse(fake.instanceFollowRedirects)
        assertEquals(null, fake.getRequestProperty("Cookie"))
        assertTrue(closed.get())
    }

    @Test fun unsafeProbeUrlsRejectedBeforeNetworkAccess() = runBlocking {
        val opened = AtomicBoolean(false)
        val p = ExtrasEndpointProbe { opened.set(true); throw AssertionError("socket opened") }
        for (url in listOf("http://example.org", "https://localhost/a",
                "https://192.168.0.1/a", "file:///etc/passwd")) {
            try {
                p.check(url)
                throw AssertionError("Unsafe URL accepted: $url")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("public HTTPS"))
            }
        }
        assertFalse(opened.get())
    }

    @Test fun dnsOrTlsFailureIsInconclusiveNotPermanentRemoval() = runBlocking {
        val p = ExtrasEndpointProbe {
            object : HttpURLConnection(URL("https://example.invalid/")) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode(): Int = throw IOException("No connection")
            }
        }
        assertEquals(ExtrasEndpointObservation.CANNOT_CONNECT,
            p.check("https://example.invalid/embed/550"))
    }

    @Test fun cancellingProbeDisconnectsAnInFlightConnection() = runBlocking {
        val called = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val p = ExtrasEndpointProbe {
            object : HttpURLConnection(URL("https://example.invalid/")) {
                override fun connect() {}
                override fun disconnect() { disconnected.countDown() }
                override fun usingProxy() = false
                override fun getResponseCode(): Int {
                    called.countDown()
                    disconnected.await(5, TimeUnit.SECONDS)
                    throw IOException("Disconnected")
                }
            }
        }
        val job = launch { p.check("https://example.invalid/embed/550") }
        assertTrue(withContext(Dispatchers.IO) { called.await(3, TimeUnit.SECONDS) })
        job.cancelAndJoin()
        assertTrue("Cancellation must disconnect network socket",
            disconnected.await(2, TimeUnit.SECONDS))
    }
}
