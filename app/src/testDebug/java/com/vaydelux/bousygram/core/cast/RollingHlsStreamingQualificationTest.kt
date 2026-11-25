package com.vaydelux.bousygram.core.cast

import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingHlsStreamingQualificationTest {
    @get:Rule val temp = TemporaryFolder()
    private fun box(type: String, data: ByteArray = byteArrayOf()) =
        ByteBuffer.allocate(8 + data.size).putInt(8 + data.size)
            .put(type.toByteArray(Charsets.US_ASCII)).put(data).array()
    private val init = box("ftyp", ByteArray(8)) + box("moov", box("mvex"))
    private val media = box("moof", ByteArray(8)) + box("mdat", byteArrayOf(1, 2, 3))
    private val verifier = object : RollingHlsVerifiedFeed.Verifier {
        override fun verifyInit(init: ByteArray) = true
        override fun verifyFragment(init: ByteArray, sequence: Long, bytes: ByteArray) =
            if (bytes.contentEquals(media)) RollingHlsVerifiedFeed.Timing(0, 1_000_000) else null
    }
    private fun source() = RollingHlsSource(4, { true }) { _, count -> ByteArray(count) }

    @Test fun nativeCallbacksPublishWithoutWholeOutputStaging() {
        val dir = temp.newFolder()
        val store = RollingHlsSegmentStore(dir, { true }, { true }, firstSequence = 1)
        RollingHlsStreamingQualification.convert(1, source(), store, { true }, { true }, { false }, verifier) { _, _, io ->
            assertEquals(4, io.read(0, ByteArray(4), 4))
            for (chunk in (init + media).toList().chunked(7)) {
                val bytes = chunk.toByteArray()
                assertTrue(io.write(bytes, bytes.size))
            }
            assertEquals(1, store.snapshot().size)
            0
        }.use { sink ->
            assertArrayEquals(init, sink.initialization())
            assertEquals(1, dir.listFiles()!!.size)
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun failureAfterPublishedFragmentRevokesStore() {
        val dir = temp.newFolder()
        val store = RollingHlsSegmentStore(dir, { true }, { true }, firstSequence = 1)
        assertThrows(IOException::class.java) {
            RollingHlsStreamingQualification.convert(2, source(), store, { true }, { true }, { false }, verifier) { _, _, io ->
                val bytes = init + media
                assertTrue(io.write(bytes, bytes.size))
                3
            }
        }
        assertTrue(store.snapshot().isEmpty())
        assertEquals(0, dir.listFiles()!!.size)
    }
}
