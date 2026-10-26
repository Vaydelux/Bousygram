package com.vaydelux.bousygram.core.media.diagnostics

import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class BlackScreenDetectorTest {

    private lateinit var detector: BlackScreenDetector
    private var virtualTimeMs: Long = 100_000L

    @Before
    fun setUp() {
        virtualTimeMs = 100_000L
        detector = BlackScreenDetector(clock = { virtualTimeMs })
    }

    @Test
    fun healthyPlayback_neverTriggersRecovery() {
        // T = 0s
        var action = detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = false,
            hasFirstFrame = true,
            currentPositionMs = 0L
        )
        assertEquals(BlackScreenRecoveryAction.NONE, action)

        // Advance 5 seconds
        virtualTimeMs += 5000L
        action = detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = false,
            hasFirstFrame = true,
            currentPositionMs = 5000L
        )
        assertEquals(BlackScreenRecoveryAction.NONE, action)
    }

    @Test
    fun blackScreenWithAudioProgression_triggersTextureViewThenMpv() {
        // T = 0s: start playing, no first frame rendered
        var action = detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = false,
            hasFirstFrame = false,
            currentPositionMs = 100L
        )
        assertEquals(BlackScreenRecoveryAction.NONE, action)

        // T = 2s: audio advancing, within timeout (<3.5s)
        virtualTimeMs += 2000L
        action = detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = false,
            hasFirstFrame = false,
            currentPositionMs = 2100L
        )
        assertEquals(BlackScreenRecoveryAction.NONE, action)

        // T = 4s: audio advanced 3900ms (>1500ms) and elapsed 4000ms (>=3500ms)
        virtualTimeMs += 2000L
        action = detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = false,
            hasFirstFrame = false,
            currentPositionMs = 4000L,
            decoderName = "c2.android.avc.decoder"
        )
        assertEquals(BlackScreenRecoveryAction.SWITCH_TO_TEXTURE_VIEW, action)
        assertNotNull(detector.lastDiagnostic)
        assertEquals(BlackScreenRecoveryAction.SWITCH_TO_TEXTURE_VIEW, detector.lastDiagnostic?.recoveryAction)

        // Reset detector for next step in recovery chain
        detector.reset()

        // Next evaluation with TextureView: still black screen after 4s
        detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = false,
            hasFirstFrame = false,
            currentPositionMs = 4000L
        )
        virtualTimeMs += 4000L
        action = detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = false,
            hasFirstFrame = false,
            currentPositionMs = 8000L
        )
        assertEquals(BlackScreenRecoveryAction.SWITCH_TO_MPV_HW, action)
    }

    @Test
    fun bufferingState_pausesWatchdog_neverTriggersRecovery() {
        // Start buffering
        detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = true,
            hasFirstFrame = false,
            currentPositionMs = 0L
        )

        // Advance 10 seconds while buffering
        virtualTimeMs += 10_000L
        val action = detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = true,
            hasFirstFrame = false,
            currentPositionMs = 0L
        )
        assertEquals(BlackScreenRecoveryAction.NONE, action)
    }

    @Test
    fun zeroDimensionsWithFirstFrame_triggersViewportFallback() {
        val action = detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = false,
            hasFirstFrame = true,
            currentPositionMs = 2000L,
            videoWidth = 0,
            videoHeight = 0
        )
        assertEquals(BlackScreenRecoveryAction.FALLBACK_VIEWPORT_DIMENSIONS, action)
    }

    @Test
    fun simulatedFailure_triggersImmediateAction() {
        detector.simulateCompositorFailure()
        val action = detector.evaluate(
            context = null,
            currentEngine = SelectedPlayerEngine.ExoPlayer,
            isPlaying = true,
            isBuffering = false,
            hasFirstFrame = true,
            currentPositionMs = 1000L
        )
        assertEquals(BlackScreenRecoveryAction.SWITCH_TO_TEXTURE_VIEW, action)
    }
}
