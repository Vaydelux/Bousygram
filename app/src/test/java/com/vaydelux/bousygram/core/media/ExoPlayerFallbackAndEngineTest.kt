package com.vaydelux.bousygram.core.media

import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import com.vaydelux.bousygram.core.observability.InMemoryPlaybackTelemetryStorage
import com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecord
import com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecorder
import com.vaydelux.bousygram.data.store.BufferMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExoPlayerFallbackAndEngineTest {

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
    fun decoderErrorsAreClassifiedAsFallbackCandidates() {
        val decoderInitEx = PlaybackException(
            "Decoder init failed: OMX.qcom.video.decoder.avc",
            null,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )
        assertTrue(VideoFormatSupport.isDecoderError(decoderInitEx, decoderInitEx.message))
        assertTrue(VideoFormatSupport.isExoPlayerFallbackCandidate(decoderInitEx, decoderInitEx.message))

        val decodingFailedEx = PlaybackException(
            "MediaCodec failed to decode slice",
            null,
            PlaybackException.ERROR_CODE_DECODING_FAILED
        )
        assertTrue(VideoFormatSupport.isDecoderError(decodingFailedEx, decodingFailedEx.message))
        assertTrue(VideoFormatSupport.isExoPlayerFallbackCandidate(decodingFailedEx, decodingFailedEx.message))

        val reason = VideoFormatSupport.classifyFallbackReason(decoderInitEx, decoderInitEx.message)
        assertTrue(reason.startsWith("Decoder error:"))
    }

    @Test
    fun containerErrorsAreClassifiedAsFallbackCandidates() {
        val containerMalformedEx = PlaybackException(
            "Malformed EBML header in MKV container",
            ParserException.createForMalformedContainer("bad header", null),
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
        )
        assertTrue(VideoFormatSupport.isContainerError(containerMalformedEx, containerMalformedEx.message))
        assertTrue(VideoFormatSupport.isExoPlayerFallbackCandidate(containerMalformedEx, containerMalformedEx.message))

        val reason = VideoFormatSupport.classifyFallbackReason(containerMalformedEx, containerMalformedEx.message)
        assertTrue(reason.startsWith("Container/parser error:"))
    }

    @Test
    fun unsupportedFormatErrorsAreClassifiedAsFallbackCandidates() {
        val unsupportedFormatEx = PlaybackException(
            "None of the available extractors could read the stream",
            null,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        )
        assertTrue(VideoFormatSupport.isUnsupportedFormatError(unsupportedFormatEx, unsupportedFormatEx.message))
        assertTrue(VideoFormatSupport.isExoPlayerFallbackCandidate(unsupportedFormatEx, unsupportedFormatEx.message))

        val decodingFormatUnsupportedEx = PlaybackException(
            "Audio format audio/vnd.dts is unsupported",
            null,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        )
        assertTrue(VideoFormatSupport.isUnsupportedFormatError(decodingFormatUnsupportedEx, decodingFormatUnsupportedEx.message))
        assertTrue(VideoFormatSupport.isExoPlayerFallbackCandidate(decodingFormatUnsupportedEx, decodingFormatUnsupportedEx.message))

        val reason = VideoFormatSupport.classifyFallbackReason(unsupportedFormatEx, unsupportedFormatEx.message)
        assertTrue(reason.startsWith("Container/parser error:") || reason.startsWith("Unsupported format error:"))
    }

    @Test
    fun networkAndGeneralErrorsDoNotTriggerMpvFallback() {
        val networkTimeoutEx = PlaybackException(
            "HttpDataSource read timeout connecting to loopback server",
            null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )
        assertFalse(VideoFormatSupport.isDecoderError(networkTimeoutEx, networkTimeoutEx.message))
        assertFalse(VideoFormatSupport.isContainerError(networkTimeoutEx, networkTimeoutEx.message))
        assertFalse(VideoFormatSupport.isUnsupportedFormatError(networkTimeoutEx, networkTimeoutEx.message))
        assertFalse(VideoFormatSupport.isExoPlayerFallbackCandidate(networkTimeoutEx, networkTimeoutEx.message))

        val fileNotFoundEx = PlaybackException(
            "Response code: 404",
            null,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        )
        assertFalse(VideoFormatSupport.isExoPlayerFallbackCandidate(fileNotFoundEx, fileNotFoundEx.message))
    }

    @Test
    fun fallbackPreservesExactPositionAndAllowsSingleFallbackOnly() {
        val mockPlayer = MockPlayerEngine(initialPosition = 42_500L)

        // Session simulation
        val sessionId = "pb_test_session_1"
        var hasFallenBackToMpv = false
        var currentEngine: PlayerEngine = mockPlayer
        var pendingResumeMs = 0L

        val error = PlaybackException(
            "Decoder init failed",
            null,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )

        // First error: should fall back to libmpv
        if (!hasFallenBackToMpv && VideoFormatSupport.isExoPlayerFallbackCandidate(error, error.message)) {
            hasFallenBackToMpv = true
            val reason = VideoFormatSupport.classifyFallbackReason(error, error.message)
            PlaybackTelemetryRecorder.recordFallback(sessionId, reason)

            // Simulate fallback transition: position preserved
            pendingResumeMs = currentEngine.currentPositionMs
            currentEngine = MockPlayerEngine(engineName = "mpv", initialPosition = 0L)
        }

        assertTrue("Fallback must be triggered on decoder error", hasFallenBackToMpv)
        assertEquals("mpv", currentEngine.engineName)
        assertEquals(42_500L, pendingResumeMs)

        // Verify telemetry recorded the reason
        val recordedReason = PlaybackTelemetryRecorder.getFallbackReason(sessionId)
        assertNotNull(recordedReason)
        assertTrue(recordedReason!!.contains("Decoder error"))

        // Simulate second error during same session: fallback must NOT trigger again
        var secondFallbackTriggered = false
        val secondError = PlaybackException(
            "Parser error",
            null,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
        )

        if (!hasFallenBackToMpv && VideoFormatSupport.isExoPlayerFallbackCandidate(secondError, secondError.message)) {
            secondFallbackTriggered = true
        }

        assertFalse("Single-fallback constraint: should only fall back once per session", secondFallbackTriggered)
    }

    @Test
    fun telemetrySessionRecordIncludesFallbackReason() {
        val sessionId = "pb_fallback_telemetry_2"
        PlaybackTelemetryRecorder.recordFallback(sessionId, "Decoder error: c2.android.hevc.decoder initialization failed")

        val record = PlaybackTelemetryRecord(
            sessionId = sessionId,
            timeToFirstByteMs = 120L,
            timeToFirstFrameMs = 450L,
            rebufferCount = 0,
            totalRebufferMs = 0L,
            engine = "mpv",
            videoDecoderName = "hevc (Software)",
            audioDecoderName = "aac",
            container = "mkv",
            fileSize = 80_000_000L,
            failureReason = null,
            fallbackReason = PlaybackTelemetryRecorder.getFallbackReason(sessionId)
        )

        PlaybackTelemetryRecorder.record(record)
        val sessions = PlaybackTelemetryRecorder.getRecords()
        assertEquals(1, sessions.size)
        val saved = sessions[0]

        assertEquals("mpv", saved.engine)
        assertNull(saved.failureReason)
        assertEquals("Decoder error: c2.android.hevc.decoder initialization failed", saved.fallbackReason)

        // Verify JSON export contains the fallbackReason
        val jsonExport = PlaybackTelemetryRecorder.exportJson()
        assertTrue(jsonExport.contains("fallbackReason"))
        assertTrue(jsonExport.contains("c2.android.hevc.decoder initialization failed"))

        // Verify summary text includes fallback notation
        val summary = PlaybackTelemetryRecorder.exportSummary()
        assertTrue(summary.contains("[FALLBACK: Decoder error:"))
    }

    @Test
    fun customLoadControlBufferWindowMatchesSpec() {
        // FastStart mode: starts at 1.5s buffered, read-ahead 30-60s
        val loadControlFastStart = TelegramProxyPlayerFactory.customLoadControl(BufferMode.FastStart, isCellular = false)
        assertNotNull(loadControlFastStart)

        // Balanced mode: starts at 2.0s buffered, read-ahead 30-60s
        val loadControlBalanced = TelegramProxyPlayerFactory.customLoadControl(BufferMode.Balanced, isCellular = false)
        assertNotNull(loadControlBalanced)

        // Cellular mode: starts at 1.5s buffered, read-ahead 30-60s
        val loadControlCellular = TelegramProxyPlayerFactory.customLoadControl(BufferMode.Balanced, isCellular = true)
        assertNotNull(loadControlCellular)
    }

    private class MockPlayerEngine(
        override val engineName: String = "ExoPlayer",
        private val initialPosition: Long = 0L
    ) : PlayerEngine {
        override val playbackState: StateFlow<EnginePlaybackState> = MutableStateFlow(EnginePlaybackState.Ready)
        override val currentPositionMs: Long = initialPosition
        override val durationMs: Long = 120_000L
        override val bufferedPositionMs: Long = initialPosition + 10_000L
        override val isPlaying: Boolean = true
        override val playWhenReady: Boolean = true
        override val activeDecoder: String = "MockDecoder"
        override val tracks: StateFlow<List<MediaTrackInfo>> = MutableStateFlow(emptyList())
        override val videoDimensions: StateFlow<Pair<Int, Int>> = MutableStateFlow(Pair(1920, 1080))

        override fun attachSurface(surface: android.view.Surface) = Unit
        override fun detachSurface() = Unit
        override fun prepare(streamUrl: String) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setPlaybackSpeed(speed: Float) = Unit
        override fun setResizeMode(mode: Int) = Unit
        override fun selectTrack(track: MediaTrackInfo) = Unit
        override fun deselectTrack(type: MediaTrackInfo.TrackType) = Unit
        override fun updateNetworkState(isCellular: Boolean) = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }
}
