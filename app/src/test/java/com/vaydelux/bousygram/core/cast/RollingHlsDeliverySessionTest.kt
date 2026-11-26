package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingHlsDeliverySessionTest {
    @get:Rule val temp = TemporaryFolder()
    private val owner = AccountSnapshot(1, 1)
    private var account = owner
    private var clock = 100L
    private var allowed = true
    private fun create(): Pair<RollingHlsDeliverySession, RollingHlsSegmentStore> {
        val store = RollingHlsSegmentStore(temp.newFolder(), { allowed && account == owner },
            { true }, firstSequence = 1)
        repeat(3) { i ->
            val data = byteArrayOf(1, 2, 3)
            assertEquals(RollingHlsSegmentStore.PublishResult.STORED, store.publish(
                RollingHlsSegmentStore.Segment(i + 1L, i * 6_000_000L, 6_000_000, 3),
                ByteArrayInputStream(data)))
        }
        val session = RollingHlsDeliverySession(owner, 7, store, { it == account },
            { allowed }, { clock }, ttlMs = 1000)
        assertTrue(session.installInitialization(ByteArray(16) { 1 }))
        return session to store
    }
    private fun send(session: RollingHlsDeliverySession, seq: String) {
        session.open(session.resourceId, session.readToken, seq)!!.use {
            assertEquals(3, it.read(ByteArray(3), 0, 3))
            it.complete(true)
        }
    }

    @Test fun playlistUsesRelativeCanonicalUrlsAndSeparateControlCredential() {
        val (s, _) = create()
        s.use {
            val playlist = s.playlist(s.resourceId, s.readToken)!!
            assertTrue(playlist.contains("#EXT-X-MEDIA-SEQUENCE:1\n"))
            assertTrue(playlist.contains("#EXTINF:6.000000,\nsegment/1.m4s?token=${s.readToken}"))
            assertFalse(playlist.contains(s.controlToken))
            assertFalse(playlist.contains("#EXT-X-ENDLIST"))
            assertNull(s.playlist("wrong", s.readToken))
            assertNull(s.playlist(s.resourceId, s.controlToken))
            assertNull(s.initialization(s.resourceId, "wrong"))
            assertArrayEquals(ByteArray(16) { 1 }, s.initialization(s.resourceId, s.readToken))
        }
    }

    @Test fun onlyFullyTransmittedContiguousSegmentsCanRetire() {
        val (s, store) = create()
        s.use {
            assertNotNull(s.metadata(s.resourceId, s.readToken, "1"))
            assertFalse(s.acknowledge(s.resourceId, s.controlToken, "1"))
            send(s, "2")
            assertFalse(s.acknowledge(s.resourceId, s.controlToken, "2"))
            s.open(s.resourceId, s.readToken, "1")!!.use { transfer ->
                assertEquals(1, transfer.read(ByteArray(1), 0, 1))
                transfer.complete(true)
            }
            assertFalse(s.acknowledge(s.resourceId, s.controlToken, "1"))
            send(s, "1")
            assertFalse(s.acknowledge(s.resourceId, s.readToken, "1"))
            assertTrue(s.acknowledge(s.resourceId, s.controlToken, "1"))
            assertFalse(s.acknowledge(s.resourceId, s.controlToken, "1"))
            assertTrue(s.acknowledge(s.resourceId, s.controlToken, "2"))
            assertEquals(listOf(3L), store.snapshot().map { it.sequence })
        }
    }

    @Test fun concurrentTransferAndMalformedSequenceDenied() {
        val (s, _) = create()
        s.use {
            for (bad in listOf("0", "01", "../1", "+1", "1/2", "9223372036854775808"))
                assertNull(s.open(s.resourceId, s.readToken, bad))
            s.open(s.resourceId, s.readToken, "1")!!.use {
                assertNull(s.open(s.resourceId, s.readToken, "2"))
            }
            send(s, "2")
        }
    }

    @Test fun accountRevocationAndExpiryDenyAllReadAndControlOperations() {
        val (s, store) = create()
        s.use {
            val lease = s.open(s.resourceId, s.readToken, "1")!!
            account = AccountSnapshot(2, 2)
            lease.use { assertThrows(IOException::class.java) { it.read(ByteArray(3), 0, 3) } }
            assertNull(s.playlist(s.resourceId, s.readToken))
            assertFalse(s.acknowledge(s.resourceId, s.controlToken, "1"))
        }
        assertEquals(0L, store.retainedBytes())
        account = owner
        val (other, _) = create()
        other.use {
            clock = 1100L
            assertNull(other.initialization(other.resourceId, other.readToken))
            assertNull(other.open(other.resourceId, other.readToken, "1"))
        }
    }

    @Test fun endListOnlyAfterExplicitProducerCompletionAndLastSegmentRetained() {
        val (s, store) = create()
        s.use {
            assertFalse(s.markComplete(2))
            assertTrue(s.markComplete(3))
            assertTrue(s.playlist(s.resourceId, s.readToken)!!.endsWith("#EXT-X-ENDLIST\n"))
            for (seq in 1..3) {
                send(s, seq.toString())
                assertEquals(seq != 3, s.acknowledge(s.resourceId, s.controlToken, seq.toString()))
            }
            assertEquals(3L, store.snapshot().single().sequence)
        }
    }
}
