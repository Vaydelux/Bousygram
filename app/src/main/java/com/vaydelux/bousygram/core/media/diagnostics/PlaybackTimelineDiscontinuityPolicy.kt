package com.vaydelux.bousygram.core.media.diagnostics

/**
 * A seek is not audio playback progress. A discontinuous timeline jump must
 * re-arm the black-screen watchdog instead of triggering decoder fallback.
 *
 * Accept playback speeds up to 3x plus scheduling jitter. Real seeks of
 * many seconds remain distinguishable even on low-end Android TV devices.
 * Use monotonic elapsed time for comparisons, never wall-clock time.
 */
internal fun playbackTimelineDiscontinuity(
    previousPositionMs: Long,
    currentPositionMs: Long,
    elapsedMs: Long
): Boolean {
    if (elapsedMs < 0L) return true
    if (previousPositionMs < 0L || currentPositionMs < 0L) return true
    if (currentPositionMs < previousPositionMs) {
        return previousPositionMs - currentPositionMs > 1_500L
    }
    val progress = currentPositionMs - previousPositionMs
    val allowance = if (elapsedMs >= (Long.MAX_VALUE - 2_000L) / 4L) {
        Long.MAX_VALUE
    } else {
        (elapsedMs * 4L + 2_000L).coerceAtLeast(5_000L)
    }
    return progress > allowance
}
