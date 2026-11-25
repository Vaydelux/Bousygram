package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingHlsDeliveryDispatcherTest {
    @get:Rule val temp = TemporaryFolder()
    private val owner = AccountSnapshot(1, 1)
    private var allowed = true
    private fun setup(): RollingHlsDeliverySession {
        val store = RollingHlsSegmentStore(temp.newFolder(), { true }, { true }, firstSequence = 1)
        val bytes = byteArrayOf(7, 8, 9)
        assertEquals(RollingHlsSegmentStore.PublishResult.STORED,
            store.publish(RollingHlsSegmentStore.Segment(1, 0, 6_000_000, 3), ByteArrayInputStream(bytes)))
        return RollingHlsDeliverySession(owner, 7, store, { it == owner }, { allowed }, { 100L })
            .also { assertTrue(it.installInitialization(ByteArray(16))) }
    }
    private fun req(s: RollingHlsDeliverySession, method: String, path: String, range: Boolean = false) =
        RollingHlsDeliveryDispatcher.Request(method, s.resourceId, path, s.readToken, hasRange = range)
    private fun ack(s: RollingHlsDeliverySession) =
        RollingHlsDeliveryDispatcher.Request("POST", s.resourceId, "ack/1", controlToken = s.controlToken)

    @Test fun headDoesNotOpenOrAcknowledgeAndGetTransmitsExactly() {
        setup().use { s ->
            val d = RollingHlsDeliveryDispatcher(s)
            d.dispatch(req(s, "HEAD", "segment/1.m4s")).use {
                assertEquals(200, it.status)
                assertEquals("3", it.headers["Content-Length"])
                it.transmit { _, _ -> fail("HEAD body") }
            }
            assertEquals(409, d.dispatch(ack(s)).status)
            val data = ByteArrayOutputStream()
            d.dispatch(req(s, "GET", "segment/1.m4s")).use {
                assertEquals(200, it.status)
                assertEquals("private, no-store", it.headers["Cache-Control"])
                it.transmit { bytes, count -> data.write(bytes, 0, count) }
            }
            assertArrayEquals(byteArrayOf(7, 8, 9), data.toByteArray())
            assertEquals(204, d.dispatch(ack(s)).status)
        }
    }

    @Test fun malformedPathRangeAndReadCredentialControlDenied() {
        setup().use { s ->
            val d = RollingHlsDeliveryDispatcher(s)
            for (path in listOf("../init.mp4", "segment/01.m4s", "segment/1.m4s?x=y",
                "segment/%31.m4s", "segment/9223372036854775808.m4s", "/index.m3u8"))
                assertEquals(404, d.dispatch(req(s, "GET", path)).status)
            assertEquals(416, d.dispatch(req(s, "GET", "segment/1.m4s", true)).status)
            assertEquals(404, d.dispatch(req(s, "POST", "ack/1")).status)
            assertEquals(405, d.dispatch(req(s, "PUT", "init.mp4")).status)
        }
    }

    @Test fun failedTransmissionNeverAcknowledgesAndAllowsRetry() {
        setup().use { s ->
            val d = RollingHlsDeliveryDispatcher(s)
            d.dispatch(req(s, "GET", "segment/1.m4s")).use {
                assertThrows(IOException::class.java) { it.transmit { _, _ -> throw IOException("network failed") } }
            }
            assertEquals(409, d.dispatch(ack(s)).status)
            d.dispatch(req(s, "GET", "segment/1.m4s")).use { it.transmit { _, _ -> } }
            assertEquals(204, d.dispatch(ack(s)).status)
        }
    }

    @Test fun routeLossIsStickyEvenWhenTransportRestores() {
        setup().use { s ->
            val d = RollingHlsDeliveryDispatcher(s)
            d.dispatch(req(s, "GET", "init.mp4")).use {
                allowed = false
                assertThrows(IOException::class.java) { it.transmit { _, _ -> fail("revoked bytes") } }
            }
            allowed = true
            assertEquals(404, d.dispatch(req(s, "GET", "index.m3u8")).status)
            assertEquals(404, d.dispatch(ack(s)).status)
        }
    }
}
