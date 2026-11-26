package com.vaydelux.bousygram.core.cast

import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingHlsFiniteQualificationTest {
    @get:Rule val temp = TemporaryFolder()
    private fun box(type: String, payload: ByteArray = byteArrayOf()) =
        ByteBuffer.allocate(8 + payload.size).putInt(8 + payload.size)
            .put(type.toByteArray(Charsets.US_ASCII)).put(payload).array()
    private fun ints(vararg v: Int) = ByteBuffer.allocate(v.size * 4).apply { v.forEach { putInt(it) } }.array()
    private val init = box("ftyp", ByteArray(8)) + box("moov", box("mvex"))
    private val fragment = box("moof", box("mfhd", ints(0, 1)) +
        box("traf", box("tfhd", ints(0x020000, 1)) + box("tfdt", ints(0, 0)) +
            box("trun", ints(1, 1, 128)))) + box("mdat", byteArrayOf(1, 2))
    private val verifier = object : RollingHlsVerifiedFeed.Verifier {
        override fun verifyInit(init: ByteArray) = init.contentEquals(this@RollingHlsFiniteQualificationTest.init)
        override fun verifyFragment(init: ByteArray, sequence: Long, bytes: ByteArray) =
            if (sequence == 1L && bytes.contentEquals(fragment)) RollingHlsVerifiedFeed.Timing(0, 1_000_000)
            else null
    }
    private fun source() = RollingHlsSource(4, { true }) { _, count -> ByteArray(count) { 3 } }

    @Test fun fakeNativeOutputFlowsThroughPrivateStagingAndVerifiedFeed() {
        val staging = temp.newFolder()
        val segments = temp.newFolder()
        val store = RollingHlsSegmentStore(segments, { true }, { true }, firstSequence = 1)
        RollingHlsFiniteQualification.convert(1, source(), staging, store, { true }, { true }, verifier) { _, _, io ->
            assertEquals(4, io.read(0, ByteArray(4), 4))
            val output = init + fragment
            assertTrue(io.write(output, output.size))
            0
        }.use { artifact ->
            assertTrue(artifact.step() is RollingHlsVerifiedFeed.Step.Stored)
            assertEquals(RollingHlsVerifiedFeed.Step.End, artifact.step())
            assertEquals(1, store.snapshot().size)
        }
        assertEquals(0, staging.listFiles()!!.size)
        assertEquals(0, segments.listFiles()!!.size)
    }

    @Test fun failedNativeTransferLeavesNoPublishedOrStagedFiles() {
        val staging = temp.newFolder()
        val segments = temp.newFolder()
        val store = RollingHlsSegmentStore(segments, { true }, { true }, firstSequence = 1)
        assertThrows(IOException::class.java) {
            RollingHlsFiniteQualification.convert(2, source(), staging, store, { true }, { true }, verifier) { _, _, io ->
                assertTrue(io.write(init, init.size))
                3
            }
        }
        assertEquals(0, staging.listFiles()!!.size)
        assertEquals(0, segments.listFiles()!!.size)
    }
}
