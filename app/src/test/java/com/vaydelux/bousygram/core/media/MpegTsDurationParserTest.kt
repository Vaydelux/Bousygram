package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpegTsDurationParserTest {
    private fun pcrPacket(pcr90k: Long, pid: Int = 0x100): ByteArray = ByteArray(188).apply {
        this[0] = 0x47
        this[1] = ((pid ushr 8) and 0x1f).toByte()
        this[2] = (pid and 0xff).toByte()
        this[3] = 0x20
        this[4] = 7
        this[5] = 0x10
        this[6] = (pcr90k ushr 25).toByte()
        this[7] = (pcr90k ushr 17).toByte()
        this[8] = (pcr90k ushr 9).toByte()
        this[9] = (pcr90k ushr 1).toByte()
        this[10] = ((pcr90k and 1L) shl 7).toByte()
    }

    @Test fun realPcrSamplesRecoverTransportStreamDuration() {
        val head = pcrPacket(90_000L * 5) + pcrPacket(90_000L * 6)
        val tail = pcrPacket(90_000L * 124) + pcrPacket(90_000L * 125)
        assertEquals(120_000L, MpegTsDurationParser.durationMs(head, tail))
    }

    @Test fun absentOrCorruptedPcrDoesNotInventDuration() {
        val valid = pcrPacket(90_000L * 5) + pcrPacket(90_000L * 6)
        assertNull(MpegTsDurationParser.durationMs(valid, ByteArray(376)))
        assertNull(MpegTsDurationParser.durationMs(ByteArray(376), valid))
    }

    @Test fun implausiblePcrDiscontinuityIsRejected() {
        val first = pcrPacket(90_000L * 120) + pcrPacket(90_000L * 121)
        val last = pcrPacket(90_000L * 1) + pcrPacket(90_000L * 2)
        assertNull(MpegTsDurationParser.durationMs(first, last))
    }

    @Test fun discontinuityFlagOnPcrRejectsFabricatedDuration() {
        val head = pcrPacket(90_000L * 5) + pcrPacket(90_000L * 6)
        val tail = pcrPacket(90_000L * 124) + pcrPacket(90_000L * 125).apply {
            this[5] = 0x90.toByte() // PCR + discontinuity flag
        }
        assertNull(MpegTsDurationParser.durationMs(head, tail))
    }

    @Test fun adaptationOnlyDiscontinuityOnSamePidRejectsDuration() {
        val head = pcrPacket(90_000L * 5) + pcrPacket(90_000L * 6)
        val discontinuityPacket = ByteArray(188).apply {
            this[0] = 0x47
            this[1] = 0x01
            this[2] = 0x00
            this[3] = 0x20
            this[4] = 1
            this[5] = 0x80.toByte()
        }
        val tail = discontinuityPacket + pcrPacket(90_000L * 125)
        assertNull(MpegTsDurationParser.durationMs(head, tail))
    }

    @Test fun adaptationLengthOutsideTsPacketDoesNotSupplyPcr() {
        val invalid = pcrPacket(90_000L * 5).apply { this[4] = 0xff.toByte() }
        val head = invalid + invalid
        val tail = pcrPacket(90_000L * 124) + pcrPacket(90_000L * 125)
        assertNull(MpegTsDurationParser.durationMs(head, tail))
    }

    @Test fun transportErrorPacketsDoNotSupplyPcr() {
        val corrupted = pcrPacket(90_000L * 5).apply { this[1] = 0x81.toByte() }
        val head = corrupted + corrupted
        val tail = pcrPacket(90_000L * 124) + pcrPacket(90_000L * 125)
        assertNull(MpegTsDurationParser.durationMs(head, tail))
    }

    @Test fun m2ts192BytePacketStrideStillRecoversDuration() {
        fun m2ts(ticks: Long) = ByteArray(4) + pcrPacket(ticks)
        val head = m2ts(90_000L * 5) + m2ts(90_000L * 6)
        val tail = m2ts(90_000L * 124) + m2ts(90_000L * 125)
        assertEquals(120_000L, MpegTsDurationParser.durationMs(head, tail))
    }

    @Test fun matchingPcrPidIsSelectedWhenFirstHeadPidIsAbsentFromTail() {
        val head = pcrPacket(90_000L * 1, 0x100) +
            pcrPacket(90_000L * 5, 0x101)
        val tail = pcrPacket(90_000L * 124, 0x101) +
            pcrPacket(90_000L * 125, 0x101)
        assertEquals(120_000L, MpegTsDurationParser.durationMs(head, tail))
    }

    @Test fun disjointPcrPidsNeverInventDuration() {
        val head = pcrPacket(90_000L * 5, 0x100) + pcrPacket(90_000L * 6, 0x100)
        val tail = pcrPacket(90_000L * 125, 0x101) + pcrPacket(90_000L * 126, 0x101)
        assertNull(MpegTsDurationParser.durationMs(head, tail))
    }

    @Test fun pcrWrapOnSamePidProducesRealTenSecondDuration() {
        val wrap = 1L shl 33
        val head = pcrPacket(wrap - 5L * 90_000L) +
            pcrPacket(wrap - 4L * 90_000L)
        val tail = pcrPacket(4L * 90_000L) + pcrPacket(5L * 90_000L)
        assertEquals(10_000L, MpegTsDurationParser.durationMs(head, tail))
    }

    @Test fun fecProtected204ByteTsPacketStrideIsSupported() {
        fun fecPacket(ticks: Long) = pcrPacket(ticks) + ByteArray(16) { 0x32 }
        val head = fecPacket(5L * 90_000L) + fecPacket(6L * 90_000L)
        val tail = fecPacket(124L * 90_000L) + fecPacket(125L * 90_000L)
        assertEquals(120_000L, MpegTsDurationParser.durationMs(head, tail))
    }

    @Test fun arbitraryPartialPacketPrefixDoesNotBreakPcrAlignment() {
        val head = ByteArray(37) { 0x20 } +
            pcrPacket(5L * 90_000L) + pcrPacket(6L * 90_000L)
        val tail = ByteArray(13) { 0x20 } +
            pcrPacket(124L * 90_000L) + pcrPacket(125L * 90_000L)
        assertEquals(120_000L, MpegTsDurationParser.durationMs(head, tail))
    }

}
