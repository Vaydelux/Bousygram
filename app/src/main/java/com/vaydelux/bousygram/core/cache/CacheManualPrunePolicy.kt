package com.vaydelux.bousygram.core.cache

/**
 * Manual safe pruning may inspect multiple bounded TDLib pages, never an
 * unbounded enumeration. Stop when quota pressure is gone or two consecutive
 * pages find no reclaimable file, because pinned/offline files are never
 * forcibly deleted to meet a target.
 */
internal fun shouldContinueManualCachePrune(
    stillOverLimit: Boolean,
    consecutiveEmptyPasses: Int
): Boolean = stillOverLimit && consecutiveEmptyPasses < 2
