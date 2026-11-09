package com.vaydelux.bousygram.core.tdlib.server

/**
 * Experimental bound for a *TDLib download request*, not an exact promise
 * about bytes physically retained by TDLib. The decoder owns actual seeks;
 * movie-duration metadata only sizes the requested byte window.
 */
internal object ForegroundPlaybackWindowPolicy {
    const val MIB = 1024L * 1024L
    const val MIN_WINDOW_BYTES = 8L * MIB
    const val NORMAL_WINDOW_BYTES = 32L * MIB
    const val UNKNOWN_DURATION_BYTES = 16L * MIB
    const val TARGET_AHEAD_MS = 120_000L
    const val LOW_FREE_BYTES = 500L * MIB

    /**
     * Returns a strictly positive TDLib DownloadFile limit, never limit=0,
     * which would request the rest of the entire file.
     */
    fun requestBytes(
        totalSizeBytes: Long,
        offsetBytes: Long,
        durationMs: Long,
        freeBytes: Long?
    ): Int {
        if (totalSizeBytes <= 0L || offsetBytes !in 0 until totalSizeBytes) return 0
        val available = totalSizeBytes - offsetBytes
        val cap = if (freeBytes == null || freeBytes < LOW_FREE_BYTES) {
            MIN_WINDOW_BYTES
        } else NORMAL_WINDOW_BYTES

        val estimated = if (durationMs > 0L) {
            // Work in doubles to avoid Long multiplication overflow for huge files.
            val requested = (totalSizeBytes.toDouble() / durationMs.toDouble()) *
                TARGET_AHEAD_MS.toDouble()
            if (!requested.isFinite()) cap else requested.toLong().coerceAtLeast(1L)
        } else UNKNOWN_DURATION_BYTES

        return minOf(estimated.coerceIn(MIN_WINDOW_BYTES, cap), available)
            .coerceAtLeast(1L).toInt()
    }

    /**
     * The last 25% of a bounded window triggers a *foreground* extension.
     * A demuxer request outside the current range also triggers a new request
     * at that exact byte offset (including MKV cues or near-EOF seeks).
     * The calling path must check for already cached bytes first.
     */
    fun needsNewWindow(
        requestedOffset: Long,
        readLength: Int,
        windowStart: Long,
        windowEndExclusive: Long
    ): Boolean {
        if (requestedOffset < 0L || readLength <= 0) return false
        if (windowStart < 0L || windowEndExclusive <= windowStart) return true
        val windowLength = windowEndExclusive - windowStart
        val margin = minOf(4L * MIB, windowLength / 4L).coerceAtLeast(1L)
        return requestedOffset < windowStart ||
            requestedOffset >= windowEndExclusive - margin ||
            readLength.toLong() > windowEndExclusive - requestedOffset
    }
}
