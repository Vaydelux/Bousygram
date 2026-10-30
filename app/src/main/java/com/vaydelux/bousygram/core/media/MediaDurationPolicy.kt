package com.vaydelux.bousygram.core.media

/**
 * The ordinary progressive player can report the duration of the currently downloaded
 * part instead of the complete file. Large-file durations from that path are untrusted.
 *
 * libmpv can expose a demuxer-derived duration on a seekable *file-backed* HTTP stream.
 * Only this distinct, verified signal may bypass the large-file restriction.
 */
internal fun trustPlayerDuration(
    reportedMs: Long,
    totalSize: Long,
    tdlibHintSeconds: Int,
    seekableContainerDuration: Boolean
): Boolean {
    if (reportedMs < 1_000L || reportedMs > 7L * 24 * 60 * 60 * 1_000L) return false
    val hintMs = tdlibHintSeconds.takeIf { it > 0 }?.times(1000L) ?: 0L
    if (hintMs > 0L) return reportedMs >= hintMs - 5_000L
    if (seekableContainerDuration) return true
    return totalSize in 1..(150L * 1024L * 1024L)
}

/**
 * Distinguishes the displayed timeline from "can seek forward/backward".
 * An unknown-length progressive stream must not show a fabricated full duration.
 */
internal fun effectiveSeekDurationMs(durationMs: Long): Long = durationMs.coerceAtLeast(0L)
