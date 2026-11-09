package com.vaydelux.bousygram.core.media

/** Saturate huge durations instead of overflowing retriever microseconds. */
internal fun seekPreviewSampleTimeUs(durationMs: Long, index: Int, count: Int): Long {
    if (durationMs <= 0L || count <= 0 || index !in 0 until count) return 0L
    val us = durationMs.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
    return (index.toLong() * (us / count)).coerceIn(0L, us)
}
