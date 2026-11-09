package com.vaydelux.bousygram.core.cache

/**
 * Recent playback is protected regardless of watched/completed state.
 * Missing timestamps follow the LRU fallback; future timestamps (e.g.
 * after a clock correction) are conservatively protected until caught up.
 */
internal fun canEvictCacheByAccessAge(
    lastAccessMs: Long?,
    nowMs: Long,
    graceMs: Long
): Boolean {
    if (lastAccessMs == null) return true
    if (lastAccessMs > nowMs) return false
    if (graceMs <= 0L) return true
    return nowMs - lastAccessMs >= graceMs
}

/** A no-pressure cache cleanup must not drop even watched media. */
internal fun needsCacheEviction(physicalOrTrackedBytes: Long, targetBytes: Long): Boolean =
    physicalOrTrackedBytes.coerceAtLeast(0L) > targetBytes.coerceAtLeast(0L)
