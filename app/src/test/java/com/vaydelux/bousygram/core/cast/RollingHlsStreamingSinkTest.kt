package com.vaydelux.bousygram.core.cast

import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingHlsStreamingSinkTest {
    @get:Rule val temp = TemporaryFolder()
    private fun box(type: String, data: ByteArray = byteArrayOf()) =
        ByteBuffer.allocate(8 + data.size).putInt(8 + data.size)
            .put(type.toByteArray(Charsets.US_ASCII)).put(data).array()
    private val init = box("ftyp", ByteArray(8)) + box("moov", box("mvex"))
    private val fragment = box("moof", ByteArray(8)) + box("mdat", byteArrayOf(1, 2, 3))
    private val verifier = object : RollingHlsVerifiedFeed.Verifier {
        override fun verifyInit(init: ByteArray) = init.contentEquals(this@RollingHlsStreamingSinkTest.init)
        override fun verifyFragment(init: ByteArray, sequence: Long, bytes: ByteArray) =
            if (bytes.contentEquals(fragment)) RollingHlsVerifiedFeed.Timing((sequence - 1) * 1_000_000, 1_000_000)
            else null
    }

    @Test fun arbitraryCallbackBoundariesAndExplicitBackpressureAcknowledgement() {
        val dir = temp.newFolder()
        val store = RollingHlsSegmentStore(dir, { true }, { true }, firstSequence = 1, capacitySegments = 1)
        var acknowledgements = 0
        RollingHlsStreamingSink(store, { true }, verifier, { next ->
            acknowledgements++
            store.retireBefore(next.sequence)
            true
        }, { 100L }).use { sink ->
            // Split headers and payloads at every byte.
            for (b in init + fragment + fragment) sink.write(b.toInt())
            sink.finish()
            assertEquals(1, acknowledgements)
            assertEquals(2L, store.snapshot().single().sequence)
            assertArrayEquals(init, sink.initialization())
            assertTrue(sink.assemblyHighWaterBytes() <= maxOf(init.size, fragment.size))
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun refusalRevokesPreviouslyPublishedSegments() {
        val dir = temp.newFolder()
        val store = RollingHlsSegmentStore(dir, { true }, { true }, firstSequence = 1, capacitySegments = 1)
        RollingHlsStreamingSink(store, { true }, verifier, { false }, { 100L }).use { sink ->
            sink.write(init + fragment)
            assertThrows(IOException::class.java) { sink.write(fragment) }
            assertTrue(store.snapshot().isEmpty())
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun incompleteOutputCannotFinish() {
        val dir = temp.newFolder()
        val store = RollingHlsSegmentStore(dir, { true }, { true }, firstSequence = 1)
        RollingHlsStreamingSink(store, { true }, verifier, { false }, { 100L }).use { sink ->
            sink.write(init + fragment.copyOf(fragment.size - 1))
            assertThrows(IOException::class.java) { sink.finish() }
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun unboundedBoxRejectedBeforeAllocation() {
        val dir = temp.newFolder()
        val store = RollingHlsSegmentStore(dir, { true }, { true }, firstSequence = 1)
        RollingHlsStreamingSink(store, { true }, verifier, { false }, { 100L }).use { sink ->
            sink.write(init)
            val huge = ByteBuffer.allocate(8).putInt(Int.MAX_VALUE).put("moof".toByteArray()).array()
            assertThrows(IOException::class.java) { sink.write(huge) }
        }
        assertEquals(0, dir.listFiles()!!.size)
    }
}
