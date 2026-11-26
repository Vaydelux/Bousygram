package com.vaydelux.bousygram.core.cast

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class RollingFmp4SampleVerifierTest {
    private fun ints(vararg v: Int) = ByteBuffer.allocate(v.size * 4).apply { v.forEach { putInt(it) } }.array()
    private fun box(type: String, data: ByteArray = byteArrayOf()) =
        ints(data.size + 8) + type.toByteArray(Charsets.US_ASCII) + data
    private fun trak(id: Int, video: Boolean): ByteArray {
        val codec = if (video) box("avcC", byteArrayOf(1, 66, 0, 30, 0xff.toByte(), 0, 0))
            else box("esds", ByteArray(8))
        val sample = box(if (video) "avc1" else "mp4a", ByteArray(if (video) 78 else 28) + codec)
        return box("trak", box("tkhd", ints(0, 0, 0, id)) +
            box("mdia", box("mdhd", ints(0, 0, 0, 1000)) +
                box("hdlr", ints(0, 0) + (if (video) "vide" else "soun").toByteArray()) +
                box("minf", box("stbl", box("stsd", ints(0, 1) + sample)))))
    }
    private val init = box("ftyp", ByteArray(8)) + box("moov", trak(1, true) + trak(2, false) +
        box("mvex", box("trex", ints(0, 1, 1, 1000, 5, 0)) + box("trex", ints(0, 2, 1, 1000, 2, 0))))
    private fun fragment(seq: Int, time: Int, overlap: Boolean = false, idr: Boolean = true,
                         badSize: Boolean = false): ByteArray {
        fun traf(id: Int, offset: Int) = box("traf", box("tfhd", ints(0x020000, id)) +
            box("tfdt", ints(0, time)) + box("trun", ints(1, 1, offset)))
        fun moof(offset: Int) = box("moof", box("mfhd", ints(0, seq)) +
            traf(1, offset) + traf(2, offset + if (overlap) 0 else 5))
        val header = moof(0)
        return moof(header.size + 8) + box("mdat", ints(if (badSize) 2 else 1) +
            byteArrayOf(if (idr) 0x65 else 0x41, 1, 2))
    }

    @Test fun exactPayloadAndContinuousDecodeTimeline() {
        val verifier = RollingFmp4SampleVerifier()
        assertTrue(verifier.verifyInit(init))
        assertEquals(RollingHlsVerifiedFeed.Timing(0, 1_000_000), verifier.verifyFragment(init, 1, fragment(1, 0)))
        assertEquals(RollingHlsVerifiedFeed.Timing(1_000_000, 1_000_000),
            verifier.verifyFragment(init, 2, fragment(2, 1000)))
    }

    @Test fun rejectsOverlapNonIdrAndMalformedNal() {
        for (bad in listOf(fragment(1, 0, overlap = true), fragment(1, 0, idr = false),
            fragment(1, 0, badSize = true))) {
            val verifier = RollingFmp4SampleVerifier()
            assertTrue(verifier.verifyInit(init))
            assertNull(verifier.verifyFragment(init, 1, bad))
            assertNull(verifier.verifyFragment(init, 1, fragment(1, 0)))
        }
    }

    @Test fun rejectsDecodeGapAndChangedInit() {
        val verifier = RollingFmp4SampleVerifier()
        assertTrue(verifier.verifyInit(init))
        assertNotNull(verifier.verifyFragment(init, 1, fragment(1, 0)))
        assertNull(verifier.verifyFragment(init, 2, fragment(2, 1001)))
        val second = RollingFmp4SampleVerifier()
        assertTrue(second.verifyInit(init))
        assertNull(second.verifyFragment(init.copyOf().also { it[8] = 1 }, 1, fragment(1, 0)))
    }
}
