package com.vaydelux.bousygram.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHealthTelemetryTest {

    @Test
    fun `SessionHealthRecord stores telemetry attributes accurately`() {
        val record = SessionHealthRecord(
            sessionId = "test_sess_1",
            fileId = 1001,
            ttfbMs = 280L,
            stallCount = 2,
            totalPlayTimeMs = 60_000L,
            totalRebufferTimeMs = 3_000L,
            rebufferRatio = 0.05f,
            networkType = "WiFi",
            decoderName = "c2.android.avc.decoder",
            engine = "libmpv"
        )
        assertEquals("test_sess_1", record.sessionId)
        assertEquals(1001, record.fileId)
        assertEquals(280L, record.ttfbMs)
        assertEquals(2, record.stallCount)
        assertEquals(0.05f, record.rebufferRatio, 0.001f)
        assertEquals("WiFi", record.networkType)
        assertEquals("libmpv", record.engine)
    }

    @Test
    fun `StreamHealthTelemetry records up to ring buffer capacity`() {
        for (i in 1..60) {
            StreamHealthTelemetry.recordSession(
                SessionHealthRecord(
                    sessionId = "sess_$i",
                    fileId = i,
                    ttfbMs = 200L + i,
                    stallCount = i % 3,
                    totalPlayTimeMs = 10_000L,
                    totalRebufferTimeMs = 500L,
                    rebufferRatio = 0.05f,
                    networkType = "WiFi",
                    decoderName = "test",
                    engine = "libmpv"
                )
            )
        }
        val records = StreamHealthTelemetry.getRecords()
        assertTrue("Ring buffer size should be <= 50, but was ${records.size}", records.size <= 50)
    }

    @Test
    fun `SessionHealthRecord carries buffer config used for the session`() {
        val mpvTv = com.vaydelux.bousygram.core.media.PlayerBufferConfig
            .resolveMpvConfig(isLowRam = false, isTv = true, isCellular = false)
        val record = SessionHealthRecord(
            sessionId = "buf_sess",
            fileId = 7,
            ttfbMs = 300L,
            stallCount = 1,
            totalPlayTimeMs = 30_000L,
            totalRebufferTimeMs = 1_500L,
            rebufferRatio = 0.05f,
            networkType = "WiFi",
            decoderName = "test",
            engine = "libmpv",
            bufferConfig = mpvTv.description
        )
        assertEquals("mpv:tv(cache=yes,max=150MB,readahead=60s,back=30MB)", record.bufferConfig)

        val legacy = record.copy(bufferConfig = null)
        assertEquals(null, legacy.bufferConfig)
    }
}
