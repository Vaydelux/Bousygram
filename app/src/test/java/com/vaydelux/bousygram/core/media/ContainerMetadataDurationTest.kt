package com.vaydelux.bousygram.core.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContainerMetadataDurationTest {
    private fun element(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }

    private fun mkvHeader(durationTicks: Double): ByteArray {
        val ebml = element(0x1a, 0x45, 0xdf, 0xa3, 0x80)
        val segment = element(0x18, 0x53, 0x80, 0x67, 0xff)
        val scale = element(0x2a, 0xd7, 0xb1, 0x83, 0x0f, 0x42, 0x40)
        val float64 = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(durationTicks).array()
        val duration = element(0x44, 0x89, 0x88) + float64
        val info = element(0x15, 0x49, 0xa9, 0x66, 0x92) + scale + duration
        return ebml + segment + info
    }

    @Test fun mkvSegmentInfoDurationIsReadFromActualEBML() {
        assertEquals(1_440_000L, ContainerHeaderParser.parseMkvDurationMs(mkvHeader(1_440_000.0)))
    }

    @Test fun corruptedAndTruncatedHeadersAreNotTrusted() {
        assertNull(ContainerHeaderParser.parseMkvDurationMs(mkvHeader(1_440_000.0).copyOf(10)))
        assertNull(ContainerHeaderParser.parseMkvDurationMs(ByteArray(128)))
        assertNull(ContainerHeaderParser.parseMkvDurationMs(mkvHeader(Double.NaN)))
    }

    @Test fun oversizedEbmlHeaderMustNotWrapIntoFakeCues() {
        val ebml = element(0x1a, 0x45, 0xdf, 0xa3)
        // Eight-byte EBML size larger than Int.MAX_VALUE.
        val oversized = element(0x01, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0x00)
        val fakeSegmentAndCues = element(
            0x18, 0x53, 0x80, 0x67, 0xff,
            0x1c, 0x53, 0xbb, 0x6b, 0x80
        )
        assertNull(ContainerHeaderParser.parseMkvCuesOffset(ebml + oversized + fakeSegmentAndCues))
    }

    @Test fun oversizedSeekHeadMustNotWrapIntoFakeCues() {
        val ebml = element(0x1a, 0x45, 0xdf, 0xa3, 0x80)
        val segment = element(0x18, 0x53, 0x80, 0x67, 0xff)
        val seekHead = element(0x11, 0x4d, 0x9b, 0x74)
        val oversized = element(0x01, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0x00)
        val fakeCues = element(0x1c, 0x53, 0xbb, 0x6b, 0x80)
        assertNull(ContainerHeaderParser.parseMkvCuesOffset(ebml + segment + seekHead + oversized + fakeCues))
    }

    @Test fun incompleteSeekEntryDoesNotFabricateCueOffset() {
        val ebml = element(0x1a, 0x45, 0xdf, 0xa3, 0x80)
        val segment = element(0x18, 0x53, 0x80, 0x67, 0xff)
        val seekId = element(0x53, 0xab, 0x84, 0x1c, 0x53, 0xbb, 0x6b)
        val incompletePosition = element(0x53, 0xac, 0x88, 0x00)
        val seek = element(0x4d, 0xbb, 0x8a) + seekId + incompletePosition
        val seekHead = element(0x11, 0x4d, 0x9b, 0x74, 0x8d) + seek
        assertNull(ContainerHeaderParser.parseMkvCuesOffset(ebml + segment + seekHead))
    }

    @Test fun oversizedMp4AtomDoesNotWrapToAnotherHeader() {
        val bigAtom = element(0, 0, 0, 1, 0x66, 0x72, 0x65, 0x65) +
            ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(0x1_0000_0010L).array()
        val fakeMoov = element(0, 0, 0, 8, 0x6d, 0x6f, 0x6f, 0x76)
        assertNull(ContainerHeaderParser.findFaststartMoovSize(bigAtom + fakeMoov))
    }

    @Test fun cuesSeekHeadUsesCorrectEbmlVariableLengthIntegers() {
        val header = element(0x1a, 0x45, 0xdf, 0xa3, 0x80)
        val segment = element(0x18, 0x53, 0x80, 0x67, 0xff)
        val seekId = element(0x53, 0xab, 0x84, 0x1c, 0x53, 0xbb, 0x6b)
        val seekPosition = element(0x53, 0xac, 0x82, 0x01, 0x00)
        val seek = element(0x4d, 0xbb, 0x8c) + seekId + seekPosition
        val seekHead = element(0x11, 0x4d, 0x9b, 0x74, 0x8f) + seek
        assertEquals(266L, ContainerHeaderParser.parseMkvCuesOffset(header + segment + seekHead))
    }
}
