package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpegTsDurationParserTest {
    private fun pcrPacket(pcr90k: Long): ByteArray = ByteArray(188).apply {
        this[0] = 0x47
        this[1] = 0x01
        this[2] = 0x00
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
}
