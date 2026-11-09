package com.vaydelux.bousygram.core.cache

import java.util.concurrent.TimeUnit

/**
 * Cache maintenance must not saturate TDLib's callback queue while the player
 * is requesting time-critical video ranges. Batches are sequential and never
 * exceed 32 outstanding GetFile or DeleteFile requests per operation.
 */
internal const val MAX_CACHE_TDLIB_INFLIGHT = 32

internal fun cacheTdLibRequestBatches(
    fileIds: List<Int>,
    maximumInFlight: Int = MAX_CACHE_TDLIB_INFLIGHT
): List<List<Int>> {
    require(maximumInFlight > 0 && maximumInFlight <= MAX_CACHE_TDLIB_INFLIGHT)
    return fileIds.chunked(maximumInFlight)
}

/**
 * Single monotonic deadline for all batches, not a fresh timeout per batch.
 * Negative elapsed time is treated conservatively as no remaining budget.
 */
internal fun cacheTdLibRemainingNanos(
    startedNs: Long,
    nowNs: Long,
    timeoutMs: Long
): Long {
    if (nowNs < startedNs || timeoutMs <= 0L) return 0L
    val budget = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    return (budget - (nowNs - startedNs)).coerceAtLeast(0L)
}
