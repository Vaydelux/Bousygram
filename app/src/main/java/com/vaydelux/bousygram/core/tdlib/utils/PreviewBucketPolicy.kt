package com.vaydelux.bousygram.core.tdlib.utils

/** Bound metadata-controlled seek timestamps before conversion to microseconds. */
internal fun boundedPreviewBucketMs(timeMs: Long, durationMs: Long): Long {
    val ceiling = if (durationMs > 0L) minOf(durationMs, Long.MAX_VALUE / 1_000L)
        else Long.MAX_VALUE / 1_000L
    return timeMs.coerceIn(0L, ceiling) / 10_000L * 10_000L
}
