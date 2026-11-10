package com.vaydelux.bousygram.core.tdlib.server

import java.math.BigInteger

private const val SEEK_BACKTRACK_BYTES = 2L * 1024L * 1024L
private const val UNKNOWN_DURATION_BYTES_PER_SECOND = 512_000L

/**
 * Range math for speculative TDLib work. Untrusted media sizes and durations
 * must not wrap into negative byte offsets or accidentally request an entire file.
 * Foreground decoder requests are not constrained by this policy.
 */
internal fun saturatingPrefetchEnd(offset: Long, length: Long): Long {
    val start = offset.coerceAtLeast(0L)
    val count = length.coerceAtLeast(0L)
    return if (start > Long.MAX_VALUE - count) Long.MAX_VALUE else start + count
}

/**
 * Download ranges use exclusive ends; seek windows use inclusive ends.
 * Adjacent ranges with no shared bytes must not keep obsolete prefetch alive.
 */
internal fun shouldCancelCacheAheadForSeek(
    activeOffset: Long,
    activeEndExclusive: Long,
    seekWindow: LongRange
): Boolean = activeEndExclusive <= seekWindow.first || activeOffset > seekWindow.last

/** 
 * Bounded adaptive playback runway for progressive native/TDLib streams.
 * Compute the mean encoded byte rate in floating point only after inputs
 * are validated. This prevents overflow of totalSize * 8000 for enormous
 * metadata and still clamps to the same established cellular/Wi-Fi budgets.
 */
internal fun boundedPlaybackWindowBytes(
    totalSize: Long,
    durationMs: Long,
    measuredThroughputBytesPerSecond: Double,
    isCellular: Boolean
): Long {
    val seconds = if (isCellular) 15.0 else 45.0
    val minCap = if (isCellular) 4L * 1024L * 1024L else 8L * 1024L * 1024L
    val maxCap = if (isCellular) 16L * 1024L * 1024L else 48L * 1024L * 1024L
    val requestedBytes = if (durationMs > 1_000L && totalSize > 0L) {
        totalSize.toDouble() * seconds * 1_000.0 / durationMs.toDouble()
    } else {
        // Preserve existing 1.5–12 Mbps fallback when stream duration is unknown.
        val safeThroughput = measuredThroughputBytesPerSecond
            .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val bitsPerSecond = (safeThroughput * 8.0).coerceIn(1_500_000.0, 12_000_000.0)
        bitsPerSecond * seconds / 8.0
    }
    return requestedBytes.toLong().coerceIn(minCap, maxCap)
}

/** Inclusive seek window shared by the pre-cache engine and chunk scheduler. */
internal fun prefetchSeekWindow(targetOffset: Long, windowBytes: Long): LongRange {
    val target = targetOffset.coerceAtLeast(0L)
    val start = target - minOf(target, SEEK_BACKTRACK_BYTES)
    return start..saturatingPrefetchEnd(target, windowBytes)
}

/**
 * Translate playback time into an approximate cache position without an
 * overflowing positionMs * totalSize. Retain exact integer precision for
 * normal files; use BigInteger only on extreme metadata overflow paths.
 */
internal fun prefetchOffsetForPosition(totalSize: Long, positionMs: Long, durationMs: Long): Long {
    if (totalSize <= 0L) return 0L
    val maximum = totalSize - 1L
    val position = positionMs.coerceAtLeast(0L)

    if (durationMs > 0L) {
        val boundedPosition = position.coerceAtMost(durationMs)
        if (boundedPosition == durationMs) return maximum
        val whole = (totalSize / durationMs) * boundedPosition
        val remainder = totalSize % durationMs
        val fraction = when {
            remainder == 0L || boundedPosition == 0L -> 0L
            remainder <= Long.MAX_VALUE / boundedPosition ->
                (remainder * boundedPosition) / durationMs
            else -> BigInteger.valueOf(remainder)
                .multiply(BigInteger.valueOf(boundedPosition))
                .divide(BigInteger.valueOf(durationMs))
                // The quotient is bounded by totalSize (< Long.MAX_VALUE).
                // BigInteger.longValueExact() is unavailable on Android API 26-30.
                .toLong()
        }
        return (whole + fraction).coerceAtMost(maximum)
    }

    // Legacy unknown-duration heuristic, now saturating instead of wrapping.
    val seconds = position / 1_000L
    return if (seconds > maximum / UNKNOWN_DURATION_BYTES_PER_SECOND) maximum
        else (seconds * UNKNOWN_DURATION_BYTES_PER_SECOND).coerceAtMost(maximum)
}
