package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.cache.TvStorageBudget

/** Admission against measured cache locations, not a new independent cache allowance. */
internal object RollingHlsBudget {
    const val MAX_WINDOW_BYTES = 32L * TvStorageBudget.MIB
    const val MAX_SEGMENT_BYTES = 8L * TvStorageBudget.MIB
    const val MAX_WINDOW_SEGMENTS = 6
    const val MAX_SEGMENT_DURATION_US = 12_000_000L
    const val MIN_FREE_BYTES = 500L * TvStorageBudget.MIB

    /** Measurements must include existing scratch and staging bytes exactly once. */
    fun admits(appCacheBytes: Long, tdLibBytes: Long, availableBytes: Long, additionalBytes: Long): Boolean {
        val limit = TvStorageBudget.TOTAL_MEDIA_CACHE_BYTES
        if (appCacheBytes !in 0L..limit || tdLibBytes !in 0L..(limit - appCacheBytes)) return false
        if (additionalBytes !in 0L..(limit - appCacheBytes - tdLibBytes)) return false
        return availableBytes >= MIN_FREE_BYTES && additionalBytes <= availableBytes - MIN_FREE_BYTES
    }
}
