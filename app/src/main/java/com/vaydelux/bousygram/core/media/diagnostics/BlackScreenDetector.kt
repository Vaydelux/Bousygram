package com.vaydelux.bousygram.core.media.diagnostics

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine

enum class BlackScreenRecoveryAction {
    NONE,
    SWITCH_TO_TEXTURE_VIEW,
    SWITCH_TO_MPV_HW,
    SWITCH_TO_MPV_SW,
    FALLBACK_VIEWPORT_DIMENSIONS
}

data class BlackScreenDiagnostic(
    val triggerReason: String,
    val recoveryAction: BlackScreenRecoveryAction,
    val audioPositionMs: Long,
    val renderedFrames: Long,
    val elapsedPlayingMs: Long,
    val decoderName: String
)

/**
 * Watchdog that detects stuck rendering (black screen with advancing audio),
 * surface compositor errors, zero-dimension viewports, and codec stalls.
 * Executes recovery actions in accordance with the Phase 3 Decision Table.
 */
class BlackScreenDetector(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "BlackScreenDetector"
        const val BLACK_SCREEN_TIMEOUT_MS = 3_500L
        const val MIN_AUDIO_PROGRESSION_MS = 1_500L
    }

    private var playingStartedAtMs: Long = 0L
    private var initialAudioPositionMs: Long = -1L
    private var hasSwitchedToTextureView: Boolean = false
    private var hasSwitchedToMpvHw: Boolean = false
    private var hasSwitchedToMpvSw: Boolean = false
    private var recoveryTriggered: Boolean = false
    private var simulatedFailure: Boolean = false

    var lastDiagnostic: BlackScreenDiagnostic? = null
        private set

    fun reset() {
        playingStartedAtMs = 0L
        initialAudioPositionMs = -1L
        recoveryTriggered = false
        simulatedFailure = false
        lastDiagnostic = null
    }

    fun simulateCompositorFailure() {
        simulatedFailure = true
    }

    /**
     * Called on each periodic playback tick (e.g. every 500ms).
     * Returns a recovery action if a black screen condition is diagnosed.
     */
    fun evaluate(
        context: Context?,
        currentEngine: SelectedPlayerEngine,
        isPlaying: Boolean,
        isBuffering: Boolean,
        hasFirstFrame: Boolean,
        currentPositionMs: Long,
        renderedFrameCount: Long = if (hasFirstFrame) 1L else 0L,
        videoWidth: Int = 1920,
        videoHeight: Int = 1080,
        decoderName: String = "Unknown",
        mediaKey: String = ""
    ): BlackScreenRecoveryAction {
        val now = clock()

        if (simulatedFailure) {
            simulatedFailure = false
            return triggerAction(
                context = context,
                action = BlackScreenRecoveryAction.SWITCH_TO_TEXTURE_VIEW,
                reason = "Simulated surface compositor failure",
                audioPos = currentPositionMs,
                frames = renderedFrameCount,
                elapsed = 4000L,
                decoder = decoderName,
                mediaKey = mediaKey
            )
        }

        // Case 5: Zero Dimensions / Aspect Ratio Mismatch
        if (isPlaying && hasFirstFrame && (videoWidth <= 0 || videoHeight <= 0)) {
            Log.w(TAG, "Zero dimensions detected ($videoWidth x $videoHeight) while playing. Applying fallback viewport.")
            return BlackScreenRecoveryAction.FALLBACK_VIEWPORT_DIMENSIONS
        }

        if (!isPlaying || isBuffering) {
            playingStartedAtMs = 0L
            initialAudioPositionMs = -1L
            return BlackScreenRecoveryAction.NONE
        }

        if (hasFirstFrame || renderedFrameCount > 0L) {
            // Video is rendering successfully
            playingStartedAtMs = 0L
            initialAudioPositionMs = -1L
            return BlackScreenRecoveryAction.NONE
        }

        // Playing, not buffering, but NO first frame rendered yet
        if (playingStartedAtMs == 0L) {
            playingStartedAtMs = now
            initialAudioPositionMs = currentPositionMs
            return BlackScreenRecoveryAction.NONE
        }

        val elapsed = now - playingStartedAtMs
        val audioAdvanced = (currentPositionMs - initialAudioPositionMs).coerceAtLeast(0L)

        // Case 1 & Case 6: Black screen with advancing audio > 3.5s
        if (elapsed >= BLACK_SCREEN_TIMEOUT_MS && audioAdvanced >= MIN_AUDIO_PROGRESSION_MS && !recoveryTriggered) {
            return when {
                // Step 1: Switch from SurfaceView to TextureView for surface compositor failure
                !hasSwitchedToTextureView -> {
                    hasSwitchedToTextureView = true
                    triggerAction(
                        context = context,
                        action = BlackScreenRecoveryAction.SWITCH_TO_TEXTURE_VIEW,
                        reason = "Surface compositor failure: Audio advanced ${audioAdvanced}ms in ${elapsed}ms without first video frame",
                        audioPos = currentPositionMs,
                        frames = renderedFrameCount,
                        elapsed = elapsed,
                        decoder = decoderName,
                        mediaKey = mediaKey
                    )
                }
                // Step 2: Switch to MPV hardware decoding
                currentEngine == SelectedPlayerEngine.ExoPlayer && !hasSwitchedToMpvHw -> {
                    hasSwitchedToMpvHw = true
                    triggerAction(
                        context = context,
                        action = BlackScreenRecoveryAction.SWITCH_TO_MPV_HW,
                        reason = "Hardware decoder black-screen: Switching to MPV",
                        audioPos = currentPositionMs,
                        frames = renderedFrameCount,
                        elapsed = elapsed,
                        decoder = decoderName,
                        mediaKey = mediaKey
                    )
                }
                // Step 3: Switch to MPV software decoding
                !hasSwitchedToMpvSw -> {
                    hasSwitchedToMpvSw = true
                    triggerAction(
                        context = context,
                        action = BlackScreenRecoveryAction.SWITCH_TO_MPV_SW,
                        reason = "Exhausted hardware decoders: Switching to MPV software decoding",
                        audioPos = currentPositionMs,
                        frames = renderedFrameCount,
                        elapsed = elapsed,
                        decoder = decoderName,
                        mediaKey = mediaKey
                    )
                }
                else -> BlackScreenRecoveryAction.NONE
            }
        }

        return BlackScreenRecoveryAction.NONE
    }

    private fun triggerAction(
        context: Context?,
        action: BlackScreenRecoveryAction,
        reason: String,
        audioPos: Long,
        frames: Long,
        elapsed: Long,
        decoder: String,
        mediaKey: String
    ): BlackScreenRecoveryAction {
        recoveryTriggered = true
        val diagnostic = BlackScreenDiagnostic(
            triggerReason = reason,
            recoveryAction = action,
            audioPositionMs = audioPos,
            renderedFrames = frames,
            elapsedPlayingMs = elapsed,
            decoderName = decoder
        )
        lastDiagnostic = diagnostic
        Log.w(TAG, "BlackScreen condition diagnosed: $reason -> Triggering $action")
        PlaybackDiagnostics.recordStreamError("BlackScreenDetector: $reason (Action: $action)")

        if (context != null && mediaKey.isNotBlank()) {
            val verdict = when (action) {
                BlackScreenRecoveryAction.SWITCH_TO_TEXTURE_VIEW ->
                    EngineVerdict(SelectedPlayerEngine.ExoPlayer, useTextureView = true, reason = reason)
                BlackScreenRecoveryAction.SWITCH_TO_MPV_HW ->
                    EngineVerdict(SelectedPlayerEngine.MPV, useTextureView = false, reason = reason)
                BlackScreenRecoveryAction.SWITCH_TO_MPV_SW ->
                    EngineVerdict(SelectedPlayerEngine.MPV, useTextureView = false, reason = reason)
                else -> null
            }
            if (verdict != null) {
                EngineVerdictStore.recordVerdict(context, mediaKey, verdict)
            }
        }

        return action
    }
}
