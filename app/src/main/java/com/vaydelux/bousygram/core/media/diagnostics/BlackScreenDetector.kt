package com.vaydelux.bousygram.core.media.diagnostics

import android.content.Context
import android.os.SystemClock
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
    // System time can jump with NTP/timezone corrections on Android TV.
    // Rendering watchdog deadlines must run on an elapsed monotonic clock.
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    companion object {
        private const val TAG = "BlackScreenDetector"
        const val BLACK_SCREEN_TIMEOUT_MS = 3_500L
        const val MIN_AUDIO_PROGRESSION_MS = 1_500L
    }

    private var playingStartedAtMs: Long? = null
    private var initialAudioPositionMs: Long = -1L
    private var lastAudioPositionMs: Long? = null
    private var lastSampleElapsedMs: Long? = null

    private fun resetWatchWindow() {
        playingStartedAtMs = null
        initialAudioPositionMs = -1L
        lastAudioPositionMs = null
        lastSampleElapsedMs = null
    }
    private var hasSwitchedToTextureView: Boolean = false
    private var hasSwitchedToMpvHw: Boolean = false
    private var hasSwitchedToMpvSw: Boolean = false
    private var recoveryTriggered: Boolean = false
    private var simulatedFailure: Boolean = false

    var lastDiagnostic: BlackScreenDiagnostic? = null
        private set

    // A fallback request is not evidence that the replacement decoder works.
    // Persist only after the player confirms that a frame was actually rendered.
    private var pendingRecovery: Pair<String, EngineVerdict>? = null

    fun confirmRenderedFrame(context: Context, mediaKey: String) {
        val pending = pendingRecovery ?: return
        if (pending.first != mediaKey) return
        pendingRecovery = null
        EngineVerdictStore.recordVerdict(context, mediaKey, pending.second)
    }

    /**
     * Re-arm the watchdog after a surface/engine transition without erasing the
     * recovery stage. A later black screen can advance from TextureView to mpv,
     * but the same stage cannot be repeated indefinitely.
     */
    fun rearmAfterTransition() {
        resetWatchWindow()
        recoveryTriggered = false
        simulatedFailure = false
        lastDiagnostic = null
    }

    /** Reset the active watch without forgetting which fallback stages were attempted. */
    fun reset() {
        rearmAfterTransition()
        pendingRecovery = null
    }

    /** Each new media item receives a fresh, independent recovery budget. */
    fun resetForNewMedia() {
        reset()
        hasSwitchedToTextureView = false
        hasSwitchedToMpvHw = false
        hasSwitchedToMpvSw = false
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
            resetWatchWindow()
            return BlackScreenRecoveryAction.NONE
        }

        if (hasFirstFrame || renderedFrameCount > 0L) {
            // Video is rendering successfully
            resetWatchWindow()
            return BlackScreenRecoveryAction.NONE
        }

        // A seek can jump the native timeline forward by hours in a single
        // callback; this is not audio output progressing while video is black.
        // Reset the watch window before deciding whether a decoder has stalled.
        val positionMs = currentPositionMs.coerceAtLeast(0L)
        val lastClock = lastSampleElapsedMs
        val lastPosition = lastAudioPositionMs
        val discontinuity = lastClock != null && lastPosition != null &&
            playbackTimelineDiscontinuity(
                previousPositionMs = lastPosition,
                currentPositionMs = positionMs,
                elapsedMs = now - lastClock
            )
        lastAudioPositionMs = positionMs
        lastSampleElapsedMs = now
        val startedAt = playingStartedAtMs
        if (startedAt == null || discontinuity || now < startedAt) {
            playingStartedAtMs = now
            initialAudioPositionMs = positionMs
            return BlackScreenRecoveryAction.NONE
        }

        val elapsed = now - startedAt
        val audioAdvanced = (positionMs - initialAudioPositionMs).coerceAtLeast(0L)

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
                pendingRecovery = mediaKey to verdict
            }
        }

        return action
    }
}
