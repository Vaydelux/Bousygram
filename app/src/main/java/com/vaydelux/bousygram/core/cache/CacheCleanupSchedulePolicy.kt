package com.vaydelux.bousygram.core.cache

/**
 * Throttle only access-triggered automatic cache sweeps. Direct user pruning
 * and post-unpin eviction remain immediate via triggerCacheCleanupIfNeeded().
 *
 * Normal playback: at most one sweep request every minute.
 * Low free space: every 15s. Critical free space: every 5s.
 * Already-running cleanup is separately coalesced by isEvictionScheduled.
 *
 * Uses monotonic elapsed time, not wall-clock time. Changing accounts resets
 * the previous schedule marker in CacheManager.
 */
internal fun shouldRequestCacheCleanupForAccess(
    previousElapsedMs: Long?,
    nowElapsedMs: Long,
    pressure: StoragePressurePolicy.Level
): Boolean {
    if (previousElapsedMs == null || nowElapsedMs < previousElapsedMs) return true
    val interval = when (pressure) {
        StoragePressurePolicy.Level.NORMAL -> 60_000L
        StoragePressurePolicy.Level.LOW -> 15_000L
        StoragePressurePolicy.Level.CRITICAL -> 5_000L
    }
    return nowElapsedMs - previousElapsedMs >= interval
}
