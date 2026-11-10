package com.vaydelux.bousygram.presentation.extras

import com.vaydelux.mediaforge.players.MediaForgePlaybackHandoff
import com.vaydelux.mediaforge.players.MediaForgePlaybackSnapshot
import com.vaydelux.mediaforge.players.MediaForgePlaybackTransition

/** Only for native playback launched from the Extras WebBridge. */
internal enum class ExtrasNativeEngine(val label: String) {
    MEDIA3("Media3"),
    LIBVLC("LibVLC")
}

/**
 * A native player handoff must carry user intent as well as seek position.
 * Switching engines, or rebuilding one after a subtitle change, is NOT a Play action.
 */
// Preserve the existing Extras ABI while centralizing policy in the SDK.
internal typealias ExtrasPlaybackHandoff = MediaForgePlaybackHandoff

internal fun extrasMedia3Handoff(
    positionMs: Long,
    seekable: Boolean,
    playWhenReady: Boolean,
    failed: Boolean,
    ended: Boolean
): ExtrasPlaybackHandoff = MediaForgePlaybackTransition.handoff(
    MediaForgePlaybackSnapshot(
        positionMs = positionMs,
        seekable = seekable,
        playRequested = playWhenReady,
        failed = failed,
        ended = ended
    )
)

internal fun extrasLibVlcHandoff(controls: ExtrasLibVlcControlState): ExtrasPlaybackHandoff =
    MediaForgePlaybackTransition.handoff(
        MediaForgePlaybackSnapshot(
            positionMs = controls.positionMs,
            pendingHandoffPositionMs = controls.pendingHandoffPositionMs,
            seekable = controls.seekable,
            playRequested = controls.playRequested,
            failed = controls.failed,
            ended = controls.ended
        )
    )

/** Never launch debug-only LibVLC for unsupported streams or production variants. */
internal fun extrasAvailableNativeEngine(
    requested: ExtrasNativeEngine,
    guardedVlcEnabled: Boolean,
    progressiveSupported: Boolean
): ExtrasNativeEngine =
    if (requested == ExtrasNativeEngine.LIBVLC &&
        (!guardedVlcEnabled || !progressiveSupported)
    ) ExtrasNativeEngine.MEDIA3 else requested
