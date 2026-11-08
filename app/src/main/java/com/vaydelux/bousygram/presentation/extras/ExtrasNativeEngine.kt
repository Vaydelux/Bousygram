package com.vaydelux.bousygram.presentation.extras

/** Only for native playback launched from the Extras WebBridge. */
internal enum class ExtrasNativeEngine(val label: String) {
    MEDIA3("Media3"),
    LIBVLC("LibVLC")
}

/**
 * A native player handoff must carry user intent as well as seek position.
 * Switching engines, or rebuilding one after a subtitle change, is NOT a Play action.
 */
internal data class ExtrasPlaybackHandoff(
    val positionMs: Long,
    val playRequested: Boolean
)

internal fun extrasMedia3Handoff(
    positionMs: Long,
    seekable: Boolean,
    playWhenReady: Boolean,
    failed: Boolean,
    ended: Boolean
): ExtrasPlaybackHandoff = ExtrasPlaybackHandoff(
    positionMs = extrasVlcHandoffPosition(positionMs, seekable),
    playRequested = shouldPreserveExtrasMedia3Resume(playWhenReady, failed, ended)
)

internal fun extrasLibVlcHandoff(controls: ExtrasLibVlcControlState): ExtrasPlaybackHandoff =
    ExtrasPlaybackHandoff(
        // A paused or not-yet-started LibVLC decoder may report 0:00 until it
        // applies the seek accepted by the source engine. Preserve that
        // already-validated origin through a rapid Media3 → LibVLC → Media3
        // round trip, including when the native decoder fails to initialize.
        positionMs = if (!controls.ended && controls.pendingHandoffPositionMs != null) {
            controls.pendingHandoffPositionMs.coerceAtLeast(0L)
        } else {
            extrasVlcHandoffPosition(controls.positionMs, controls.seekable)
        },
        playRequested = controls.playRequested && !controls.failed && !controls.ended
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
