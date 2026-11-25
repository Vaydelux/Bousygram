package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingHlsControlledQualificationTest {
    @get:Rule val temp = TemporaryFolder()
    private val owner = AccountSnapshot(1, 1)
    private fun box(type: String, data: ByteArray = byteArrayOf()) =
        ByteBuffer.allocate(8 + data.size).putInt(8 + data.size)
            .put(type.toByteArray(Charsets.US_ASCII)).put(data).array()
    private val init = box("ftyp", ByteArray(8)) + box("moov", box("mvex"))
    private val media = box("moof", ByteArray(8)) + box("mdat", byteArrayOf(7, 8, 9))
    private val verifier = object : RollingHlsVerifiedFeed.Verifier {
        override fun verifyInit(init: ByteArray) = init.contentEquals(this@RollingHlsControlledQualificationTest.init)
        override fun verifyFragment(init: ByteArray, sequence: Long, bytes: ByteArray) =
            if (bytes.contentEquals(media)) RollingHlsVerifiedFeed.Timing((sequence - 1) * 6_000_000, 6_000_000)
            else null
    }

    @Test fun longLogicalStreamThroughDispatcherKeepsTwoSegmentsAndExactBytes() {
        val dir = temp.newFolder()
        val received = ByteArrayOutputStream()
        val initReceived = ByteArrayOutputStream()
        var lastConsumed = 0L
        RollingHlsControlledQualification(owner, 7, dir, { it == owner }, { true }, { 100L },
            { true }, { _, bytes, count -> received.write(bytes, 0, count) },
            { segment -> assertEquals(lastConsumed + 1, segment.sequence); lastConsumed++; true }, verifier).use { q ->
            for (byte in init) q.write(byteArrayOf(byte), 0, 1)
            assertTrue(q.receiveInitialization { bytes, count -> initReceived.write(bytes, 0, count) })
            assertArrayEquals(init, initReceived.toByteArray())
            repeat(60) {
                q.write(media, 0, media.size)
                assertTrue(q.snapshot().size <= 2)
                assertTrue(q.retainedBytes() <= 2L * media.size)
            }
            q.finish()
            assertEquals(58L, q.acknowledgedCount())
            assertTrue(q.playlist()!!.endsWith("#EXT-X-ENDLIST\n"))
            assertTrue(q.drainAcknowledged())
            assertEquals(59L, q.acknowledgedCount())
            assertEquals(60L, q.snapshot().single().sequence)
            val expected = ByteArrayOutputStream().apply { repeat(59) { write(media) } }.toByteArray()
            assertArrayEquals(expected, received.toByteArray())
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun successfulTransmissionWithoutConsumptionStopsAndRevokes() {
        val dir = temp.newFolder()
        RollingHlsControlledQualification(owner, 7, dir, { it == owner }, { true }, { 100L },
            { true }, { _, _, _ -> }, { false }, verifier).use { q ->
            q.write(init, 0, init.size)
            assertTrue(q.receiveInitialization { _, _ -> })
            repeat(2) { q.write(media, 0, media.size) }
            assertThrows(IOException::class.java) { q.write(media, 0, media.size) }
            assertEquals(0L, q.acknowledgedCount())
            assertNull(q.playlist())
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun accountLossDuringReceiverConsumptionPreventsRetirement() {
        val dir = temp.newFolder()
        var current = owner
        RollingHlsControlledQualification(owner, 7, dir, { it == current }, { true }, { 100L },
            { true }, { _, _, _ -> current = AccountSnapshot(2, 2) }, { true }, verifier).use { q ->
            q.write(init, 0, init.size)
            assertTrue(q.receiveInitialization { _, _ -> })
            repeat(2) { q.write(media, 0, media.size) }
            assertThrows(IOException::class.java) { q.write(media, 0, media.size) }
            assertEquals(0L, q.acknowledgedCount())
            assertNull(q.playlist())
        }
        assertEquals(0, dir.listFiles()!!.size)
    }
}
