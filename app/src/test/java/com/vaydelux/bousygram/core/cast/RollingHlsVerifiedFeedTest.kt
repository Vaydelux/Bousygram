package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingHlsVerifiedFeedTest {
    @get:Rule val temp = TemporaryFolder()

    private fun box(type: String, payload: ByteArray = byteArrayOf()) =
        ByteBuffer.allocate(8 + payload.size).putInt(8 + payload.size)
            .put(type.toByteArray(Charsets.US_ASCII)).put(payload).array()
    private fun ints(vararg values: Int) = ByteBuffer.allocate(values.size * 4)
        .apply { values.forEach { putInt(it) } }.array()
    private fun init() = box("ftyp", ByteArray(8)) + box("moov", box("mvex"))
    private fun fragment(sequence: Int) = box("moof", box("mfhd", ints(0, sequence)) +
        box("traf", box("tfhd", ints(0x020000, 1)) + box("tfdt", ints(0, sequence - 1)) +
            box("trun", ints(1, 1, 128)))) + box("mdat", byteArrayOf(7, 8))
    private class FakeVerifier(var reject: Long? = null) : RollingHlsVerifiedFeed.Verifier {
        var calls = 0
        override fun verifyInit(init: ByteArray) = init.isNotEmpty()
        override fun verifyFragment(init: ByteArray, sequence: Long, bytes: ByteArray): RollingHlsVerifiedFeed.Timing? {
            calls++
            if (sequence == reject) return null
            return RollingHlsVerifiedFeed.Timing((sequence - 1) * 1_000_000, 1_000_000)
        }
    }
    private fun store(dir: File, permit: () -> Boolean = { true }) =
        RollingHlsSegmentStore(dir, permit, { true }, firstSequence = 1, capacitySegments = 1)

    @Test fun backpressureRetainsExactUnitAndDoesNotReverify() {
        val dir = temp.newFolder()
        val store = store(dir)
        val verifier = FakeVerifier()
        RollingHlsVerifiedFeed(ByteArrayInputStream(init() + fragment(1) + fragment(2)), store,
            { true }, verifier).use { feed ->
            assertTrue(feed.step() is RollingHlsVerifiedFeed.Step.Stored)
            assertArrayEquals(init(), feed.initialization())
            assertEquals(RollingHlsVerifiedFeed.Step.Backpressure, feed.step())
            assertEquals(RollingHlsVerifiedFeed.Step.Backpressure, feed.step())
            assertEquals(2, verifier.calls)
            store.retireBefore(2)
            assertTrue(feed.step() is RollingHlsVerifiedFeed.Step.Stored)
            val lease = store.open(2)!!
            lease.use {
                val data = ByteArray(fragment(2).size)
                assertEquals(data.size, it.read(data, 0, data.size))
                assertArrayEquals(fragment(2), data)
            }
            assertEquals(RollingHlsVerifiedFeed.Step.End, feed.step())
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun rejectedFragmentRevokesAndDeletesPreviouslyStoredMedia() {
        val dir = temp.newFolder()
        val store = store(dir)
        RollingHlsVerifiedFeed(ByteArrayInputStream(init() + fragment(1) + fragment(2)), store,
            { true }, FakeVerifier(2)).use { feed ->
            feed.step()
            assertThrows(IOException::class.java) { feed.step() }
            assertTrue(store.snapshot().isEmpty())
            assertNull(store.open(1))
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun revokeWhileBackpressuredDiscardsPendingUnit() {
        val dir = temp.newFolder()
        var allowed = true
        val store = store(dir) { allowed }
        RollingHlsVerifiedFeed(ByteArrayInputStream(init() + fragment(1) + fragment(2)), store,
            { allowed }, FakeVerifier()).use { feed ->
            feed.step()
            assertEquals(RollingHlsVerifiedFeed.Step.Backpressure, feed.step())
            allowed = false
            assertThrows(IOException::class.java) { feed.step() }
            assertNull(store.open(1))
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun noMediaOrTruncatedMediaFailsClosed() {
        for (bytes in listOf(init(), init() + fragment(1).dropLast(1).toByteArray())) {
            val dir = temp.newFolder()
            val store = store(dir)
            RollingHlsVerifiedFeed(ByteArrayInputStream(bytes), store, { true }, FakeVerifier()).use { feed ->
                assertThrows(IOException::class.java) { feed.step() }
            }
            assertEquals(0, dir.listFiles()!!.size)
        }
    }
}
