package com.vaydelux.bousygram.presentation.extras

import java.io.ByteArrayInputStream
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

class ExtrasHttpTransportTest {
    @Test fun parsesSuccessfulBoundedJsonWithoutNetworkAccess() = runBlocking {
        val fake = object : HttpURLConnection(URL("https://example.invalid/")) {
            val disconnected = AtomicBoolean(false)
            override fun connect() {}
            override fun disconnect() { disconnected.set(true) }
            override fun usingProxy() = false
            override fun getResponseCode() = 200
            override fun getInputStream() = ByteArrayInputStream("{\"ok\":true}".toByteArray())
        }
        val response = ExtrasHttpTransport { fake }.execute(
            "https://example.invalid/", null, 1024
        )
        assertEquals(200, response.status)
        assertEquals("{\"ok\":true}", response.json)
        assertTrue(fake.disconnected.get())
    }

    @Test fun rejectsPayloadLargerThanBoundedResponseLimit() = runBlocking {
        val fake = object : HttpURLConnection(URL("https://example.invalid/")) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = 200
            override fun getInputStream() = ByteArrayInputStream(ByteArray(64) { 'x'.code.toByte() })
        }
        try {
            ExtrasHttpTransport { fake }.execute("https://example.invalid/", null, 16)
            throw AssertionError("Oversized response was accepted")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("size limit"))
        }
    }

    @Test fun cancelledSearchDisconnectsBlockedRequestWithoutWaitingForTimeout() = runBlocking {
        val reading = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val fake = object : HttpURLConnection(URL("https://example.invalid/")) {
            override fun connect() {}
            override fun disconnect() { disconnected.countDown() }
            override fun usingProxy() = false
            override fun getResponseCode(): Int {
                reading.countDown()
                if (!disconnected.await(5, TimeUnit.SECONDS)) {
                    throw IOException("Fake HTTP connection was not disconnected")
                }
                throw IOException("Disconnected by cancel")
            }
        }
        val job = launch {
            ExtrasHttpTransport { fake }.execute("https://example.invalid/", null, 1024)
        }
        assertTrue("Worker did not begin HTTP read",
            withContext(Dispatchers.IO) { reading.await(2, TimeUnit.SECONDS) })
        assertFalse("Connection disconnected too soon", disconnected.count == 0L)
        job.cancelAndJoin()
        assertTrue("Cancellation did not disconnect socket",
            disconnected.await(1, TimeUnit.SECONDS))
    }

    @Test fun insecureAndPrivateCatalogUrlsAreRejectedBeforeOpeningSocket() = runBlocking {
        val opened = AtomicBoolean(false)
        val transport = ExtrasHttpTransport {
            opened.set(true)
            throw AssertionError("Rejected catalog URL must never open a connection")
        }
        val rejected = listOf(
            "http://api.themoviedb.org/3/movie/1",
            "https://localhost/api",
            "https://127.0.0.1/api",
            "https://192.168.1.1/api",
            "https://public.example.org:8443/api",
            "file:///etc/passwd"
        )
        for (url in rejected) {
            try {
                transport.execute(url, null, 1024)
                throw AssertionError("Unsafe catalog URL was accepted: $url")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("HTTPS"))
            }
        }
        assertFalse(opened.get())
    }

    @Test fun errorStatusIsReturnedWithoutReadingErrorStream() = runBlocking {
        val fake = object : HttpURLConnection(URL("https://example.invalid/")) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = 429
            override fun getHeaderField(name: String): String? =
                if (name == "Retry-After") "15" else null
            override fun getInputStream(): java.io.InputStream =
                throw AssertionError("HTTP error payload must not be downloaded")
        }
        val result = ExtrasHttpTransport { fake }.execute("https://example.invalid/", null, 1024)
        assertEquals(429, result.status)
        assertEquals("15", result.retryAfter)
        assertEquals(null, result.json)
    }
}
