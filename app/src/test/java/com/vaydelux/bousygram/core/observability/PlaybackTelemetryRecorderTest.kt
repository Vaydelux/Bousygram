package com.vaydelux.bousygram.core.observability

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackTelemetryRecorderTest {

    private lateinit var testStorage: InMemoryPlaybackTelemetryStorage

    @Before
    fun setUp() {
        testStorage = InMemoryPlaybackTelemetryStorage()
        PlaybackTelemetryRecorder.setStorageForTesting(testStorage)
        PlaybackTelemetryRecorder.clear()
    }

    @After
    fun tearDown() {
        PlaybackTelemetryRecorder.clear()
    }

    @Test
    fun recordsAllTenRequiredFieldsAccurately() {
        val record = PlaybackTelemetryRecord(
            sessionId = "sess_test_101",
            timeToFirstByteMs = 125L,
            timeToFirstFrameMs = 380L,
            rebufferCount = 3,
            totalRebufferMs = 2450L,
            engine = "ExoPlayer",
            videoDecoderName = "c2.android.avc.decoder",
            audioDecoderName = "c2.android.aac.decoder",
            container = "mp4",
            fileSize = 52_428_800L,
            failureReason = null
        )

        PlaybackTelemetryRecorder.record(record)
        val records = PlaybackTelemetryRecorder.getRecords()

        assertEquals(1, records.size)
        val retrieved = records[0]

        // Verify all 10 required telemetry fields
        assertEquals(125L, retrieved.timeToFirstByteMs)
        assertEquals(380L, retrieved.timeToFirstFrameMs)
        assertEquals(3, retrieved.rebufferCount)
        assertEquals(2450L, retrieved.totalRebufferMs)
        assertEquals("ExoPlayer", retrieved.engine)
        assertEquals("c2.android.avc.decoder", retrieved.videoDecoderName)
        assertEquals("c2.android.aac.decoder", retrieved.audioDecoderName)
        assertEquals("mp4", retrieved.container)
        assertEquals(52_428_800L, retrieved.fileSize)
        assertNull(retrieved.failureReason)
    }

    @Test
    fun recordsFailureReasonWhenPlaybackErrorsOut() {
        val errorRecord = PlaybackTelemetryRecord(
            sessionId = "sess_error_102",
            timeToFirstByteMs = 210L,
            timeToFirstFrameMs = 0L,
            rebufferCount = 0,
            totalRebufferMs = 0L,
            engine = "mpv",
            videoDecoderName = "hevc (Hardware)",
            audioDecoderName = "eac3",
            container = "mkv",
            fileSize = 104_857_600L,
            failureReason = "Demuxer failed to parse header at offset 0"
        )

        PlaybackTelemetryRecorder.record(errorRecord)
        val retrieved = PlaybackTelemetryRecorder.getRecords().single()

        assertEquals("mpv", retrieved.engine)
        assertEquals("mkv", retrieved.container)
        assertEquals("Demuxer failed to parse header at offset 0", retrieved.failureReason)
    }

    @Test
    fun ringBufferEnforcesFiftySessionLimitWithFifoEviction() {
        // Record 55 sessions
        for (i in 1..55) {
            PlaybackTelemetryRecorder.record(
                PlaybackTelemetryRecord(
                    sessionId = "sess_$i",
                    timeToFirstByteMs = 100L + i,
                    timeToFirstFrameMs = 200L + i,
                    rebufferCount = i % 3,
                    totalRebufferMs = (i * 100).toLong(),
                    engine = if (i % 2 == 0) "ExoPlayer" else "mpv",
                    videoDecoderName = "decoder_$i",
                    audioDecoderName = "audio_$i",
                    container = if (i % 2 == 0) "mp4" else "mkv",
                    fileSize = (i * 1_000_000).toLong(),
                    failureReason = if (i == 50) "Network dropped" else null
                )
            )
        }

        val records = PlaybackTelemetryRecorder.getRecords()

        // Ring buffer must be capped at exactly MAX_SESSIONS (50)
        assertEquals(50, records.size)

        // FIFO: Sessions 1..5 must have been evicted, oldest remaining must be sess_6
        assertEquals("sess_6", records.first().sessionId)

        // Most recent session must be sess_55
        assertEquals("sess_55", records.last().sessionId)

        // Verify storage also persisted exactly 50 records
        val persisted = testStorage.loadSessions()
        assertEquals(50, persisted.size)
        assertEquals("sess_6", persisted.first().sessionId)
        assertEquals("sess_55", persisted.last().sessionId)
    }

    @Test
    fun jsonRoundTripSerializationAndDeserialization() {
        val original = PlaybackTelemetryRecord(
            sessionId = "sess_json_test",
            timeToFirstByteMs = 150L,
            timeToFirstFrameMs = 320L,
            rebufferCount = 1,
            totalRebufferMs = 500L,
            engine = "ExoPlayer",
            videoDecoderName = "c2.android.avc.decoder",
            audioDecoderName = "c2.android.aac.decoder",
            container = "webm",
            fileSize = 34_567_890L,
            failureReason = "Codec error: \"failed with code 0x123\\test\""
        )

        val json = PlaybackTelemetrySerializer.recordToJson(original)
        val deserialized = PlaybackTelemetrySerializer.parseRecord(json)

        assertNotNull(deserialized)
        assertEquals(original.sessionId, deserialized!!.sessionId)
        assertEquals(original.timeToFirstByteMs, deserialized.timeToFirstByteMs)
        assertEquals(original.timeToFirstFrameMs, deserialized.timeToFirstFrameMs)
        assertEquals(original.rebufferCount, deserialized.rebufferCount)
        assertEquals(original.totalRebufferMs, deserialized.totalRebufferMs)
        assertEquals(original.engine, deserialized.engine)
        assertEquals(original.videoDecoderName, deserialized.videoDecoderName)
        assertEquals(original.audioDecoderName, deserialized.audioDecoderName)
        assertEquals(original.container, deserialized.container)
        assertEquals(original.fileSize, deserialized.fileSize)
        assertEquals(original.failureReason, deserialized.failureReason)
    }

    @Test
    fun exportJsonProducesValidStructuredJson() {
        PlaybackTelemetryRecorder.record(
            PlaybackTelemetryRecord(
                sessionId = "sess_1",
                timeToFirstByteMs = 100L,
                timeToFirstFrameMs = 250L,
                rebufferCount = 1,
                totalRebufferMs = 400L,
                engine = "ExoPlayer",
                videoDecoderName = "hw_avc",
                audioDecoderName = "hw_aac",
                container = "mp4",
                fileSize = 10_000_000L,
                failureReason = null
            )
        )
        PlaybackTelemetryRecorder.record(
            PlaybackTelemetryRecord(
                sessionId = "sess_2",
                timeToFirstByteMs = 200L,
                timeToFirstFrameMs = 350L,
                rebufferCount = 2,
                totalRebufferMs = 800L,
                engine = "mpv",
                videoDecoderName = "h264 (Software)",
                audioDecoderName = "opus",
                container = "mkv",
                fileSize = 20_000_000L,
                failureReason = "IO error"
            )
        )

        val json = PlaybackTelemetryRecorder.exportJson()

        assertTrue(json.contains("\"sessionCount\": 2"))
        assertTrue(json.contains("\"avgTimeToFirstByteMs\": 150.0"))
        assertTrue(json.contains("\"avgTimeToFirstFrameMs\": 300.0"))
        assertTrue(json.contains("\"totalRebuffers\": 3"))
        assertTrue(json.contains("\"totalRebufferMs\": 1200"))
        assertTrue(json.contains("\"failureCount\": 1"))
        assertTrue(json.contains("\"sessionId\": \"sess_1\""))
        assertTrue(json.contains("\"sessionId\": \"sess_2\""))
        assertTrue(json.contains("\"failureReason\": \"IO error\""))
        assertTrue(json.contains("\"failureReason\": null"))
    }

    @Test
    fun exportSummaryProducesFormattedDiagnosticReport() {
        PlaybackTelemetryRecorder.record(
            PlaybackTelemetryRecord(
                sessionId = "sess_summary_1",
                timeToFirstByteMs = 140L,
                timeToFirstFrameMs = 310L,
                rebufferCount = 0,
                totalRebufferMs = 0L,
                engine = "ExoPlayer",
                videoDecoderName = "c2.android.avc.decoder",
                audioDecoderName = "c2.android.aac.decoder",
                container = "mp4",
                fileSize = 25_000_000L,
                failureReason = null
            )
        )

        val summary = PlaybackTelemetryRecorder.exportSummary()

        assertTrue(summary.contains("Local Playback Telemetry (1 sessions recorded"))
        assertTrue(summary.contains("[ExoPlayer] MP4"))
        assertTrue(summary.contains("TTFB=140ms TTFF=310ms"))
        assertTrue(summary.contains("rebuffers=0 (0ms)"))
        assertTrue(summary.contains("vDec=c2.android.avc.decoder"))
        assertFalse(summary.contains("FAILURE"))
    }

    @Test
    fun firstByteTrackingLifecycle() {
        val fileId = 42
        val startTime = System.currentTimeMillis() - 150L

        assertFalse(PlaybackTelemetryRecorder.hasFirstByte(fileId))

        // First byte arrives from server/scheduler
        PlaybackTelemetryRecorder.notifyFirstByte(fileId)
        assertTrue(PlaybackTelemetryRecorder.hasFirstByte(fileId))

        // Subsequent notification for same fileId does not overwrite initial timestamp
        val elapsed = PlaybackTelemetryRecorder.consumeFirstByteElapsed(fileId, startTime)
        assertTrue("Elapsed TTFB must be >= 150ms, was $elapsed", elapsed >= 150L)

        // After consumption, registry is cleared
        assertFalse(PlaybackTelemetryRecorder.hasFirstByte(fileId))
        assertEquals(0L, PlaybackTelemetryRecorder.consumeFirstByteElapsed(fileId, startTime))
    }

    @Test
    fun clearResetsInMemoryAndStorage() {
        PlaybackTelemetryRecorder.record(
            PlaybackTelemetryRecord(
                sessionId = "sess_to_clear",
                timeToFirstByteMs = 100L,
                timeToFirstFrameMs = 200L,
                rebufferCount = 0,
                totalRebufferMs = 0L,
                engine = "ExoPlayer",
                videoDecoderName = "vDec",
                audioDecoderName = "aDec",
                container = "mp4",
                fileSize = 1000L
            )
        )
        assertEquals(1, PlaybackTelemetryRecorder.getRecords().size)

        PlaybackTelemetryRecorder.clear()

        assertTrue(PlaybackTelemetryRecorder.getRecords().isEmpty())
        assertTrue(testStorage.loadSessions().isEmpty())
        assertEquals("no playback sessions", PlaybackTelemetryRecorder.summary())
        assertEquals("No playback telemetry recorded yet.", PlaybackTelemetryRecorder.exportSummary())
    }

    @Test
    fun recordsAndSerializesNewTelemetryFields() {
        val record = PlaybackTelemetryRecord(
            sessionId = "sess_telemetry_extended",
            timeToFirstByteMs = 150L,
            timeToFirstFrameMs = 320L,
            rebufferCount = 1,
            totalRebufferMs = 400L,
            engine = "ExoPlayer",
            videoDecoderName = "c2.android.avc.decoder",
            audioDecoderName = "c2.android.aac.decoder",
            container = "mp4",
            fileSize = 104_857_600L,
            failureReason = null,
            fallbackReason = null,
            isFullyCachedAtStart = true,
            timeToHeaderChunkMs = 45L,
            timeToTailChunkMs = 120L,
            averageThroughputBytesPerSec = 4_500_000L
        )

        PlaybackTelemetryRecorder.record(record)
        val retrieved = PlaybackTelemetryRecorder.getRecords().single()

        assertTrue(retrieved.isFullyCachedAtStart)
        assertEquals(45L, retrieved.timeToHeaderChunkMs)
        assertEquals(120L, retrieved.timeToTailChunkMs)
        assertEquals(4_500_000L, retrieved.averageThroughputBytesPerSec)

        // Verify JSON serialization round-trip
        val json = PlaybackTelemetrySerializer.recordToJson(record)
        val parsed = PlaybackTelemetrySerializer.parseRecord(json)
        assertNotNull(parsed)
        assertTrue(parsed!!.isFullyCachedAtStart)
        assertEquals(45L, parsed.timeToHeaderChunkMs)
        assertEquals(120L, parsed.timeToTailChunkMs)
        assertEquals(4_500_000L, parsed.averageThroughputBytesPerSec)

        val summary = PlaybackTelemetryRecorder.exportSummary()
        assertTrue(summary.contains("cached=true"))
        assertTrue(summary.contains("header=45ms"))
        assertTrue(summary.contains("tail=120ms"))

        val exportJson = PlaybackTelemetryRecorder.exportJson()
        assertTrue(exportJson.contains("\"isFullyCachedAtStart\": true"))
        assertTrue(exportJson.contains("\"timeToHeaderChunkMs\": 45"))
        assertTrue(exportJson.contains("\"timeToTailChunkMs\": 120"))
        assertTrue(exportJson.contains("\"averageThroughputBytesPerSec\": 4500000"))
    }

    @Test
    fun telemetryTrackingLifecycleMethods() {
        val fileId = 99
        PlaybackTelemetryRecorder.recordFullyCachedAtStart(fileId, true)
        PlaybackTelemetryRecorder.recordHeaderChunkTime(fileId, 55L)
        PlaybackTelemetryRecorder.recordTailChunkTime(fileId, 110L)
        PlaybackTelemetryRecorder.recordThroughput(fileId, 3_200_000L)

        assertEquals(55L, PlaybackTelemetryRecorder.peekHeaderChunkTime(fileId))
        assertTrue(PlaybackTelemetryRecorder.consumeFullyCachedAtStart(fileId))
        assertFalse(PlaybackTelemetryRecorder.consumeFullyCachedAtStart(fileId))

        assertEquals(55L, PlaybackTelemetryRecorder.consumeHeaderChunkTime(fileId))
        assertNull(PlaybackTelemetryRecorder.consumeHeaderChunkTime(fileId))

        assertEquals(110L, PlaybackTelemetryRecorder.consumeTailChunkTime(fileId))
        assertNull(PlaybackTelemetryRecorder.consumeTailChunkTime(fileId))

        assertEquals(3_200_000L, PlaybackTelemetryRecorder.consumeThroughput(fileId))
        assertNull(PlaybackTelemetryRecorder.consumeThroughput(fileId))
    }

    @Test
    fun recordsBufferConfigAndSerializesToTelemetry() {
        val bufferConfigStr = "exo:normal(min=30000ms,max=120000ms,play=2500ms,rebuf=5000ms,back=30000ms)"
        val record = PlaybackTelemetryRecord(
            sessionId = "sess_buffer_cfg_1",
            timeToFirstByteMs = 150L,
            timeToFirstFrameMs = 400L,
            rebufferCount = 1,
            totalRebufferMs = 850L,
            engine = "ExoPlayer",
            videoDecoderName = "c2.android.avc.decoder",
            audioDecoderName = "c2.android.aac.decoder",
            container = "mp4",
            fileSize = 75_000_000L,
            bufferConfig = bufferConfigStr
        )

        PlaybackTelemetryRecorder.record(record)
        val retrieved = PlaybackTelemetryRecorder.getRecords().single()
        assertEquals(bufferConfigStr, retrieved.bufferConfig)

        // Verify JSON roundtrip
        val json = PlaybackTelemetrySerializer.recordToJson(record)
        assertTrue(json.contains("\"bufferConfig\":\"exo:normal(min=30000ms,max=120000ms,play=2500ms,rebuf=5000ms,back=30000ms)\""))
        val parsed = PlaybackTelemetrySerializer.parseRecord(json)
        assertNotNull(parsed)
        assertEquals(bufferConfigStr, parsed?.bufferConfig)

        // Verify exportSummary contains buf string
        val summary = PlaybackTelemetryRecorder.exportSummary()
        assertTrue(summary.contains("buf=[exo:normal(min=30000ms,max=120000ms,play=2500ms,rebuf=5000ms,back=30000ms)]"))

        // Verify exportJson contains bufferConfig
        val exportJson = PlaybackTelemetryRecorder.exportJson()
        assertTrue(exportJson.contains("\"bufferConfig\": \"exo:normal(min=30000ms,max=120000ms,play=2500ms,rebuf=5000ms,back=30000ms)\""))
    }
}
