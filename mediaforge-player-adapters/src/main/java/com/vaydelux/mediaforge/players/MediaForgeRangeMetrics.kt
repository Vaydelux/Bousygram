package com.vaydelux.mediaforge.players

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Fixed-size, per-guarded-source diagnostics for LibVLC HTTP byte ranges.
 *
 * Never stores source URLs, headers, response bodies, credentials or user IDs.
 * Stats are local to the current native playback session; no disk, logging or
 * network export. Atomic counters allow a UI snapshot while the FD worker runs.
 */
data class MediaForgeRangeMetricsSnapshot(
    val readCalls: Long,
    val readBytes: Long,
    val cacheHits: Long,
    val httpRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val downloadedBytes: Long,
    val totalFetchMillis: Long,
    val longestFetchMillis: Long,
    val retainedCacheBytes: Int
) {
    val cacheHitPercent: Int
        get() {
            val opportunities = cacheHits + httpRequests
            return if (opportunities <= 0L) 0
            else ((cacheHits.toDouble() * 100.0) / opportunities.toDouble())
                .toInt().coerceIn(0, 100)
        }
}

class MediaForgeRangeMetrics {
    private val readCalls = AtomicLong()
    private val readBytes = AtomicLong()
    private val cacheHits = AtomicLong()
    private val httpRequests = AtomicLong()
    private val successfulRequests = AtomicLong()
    private val failedRequests = AtomicLong()
    private val downloadedBytes = AtomicLong()
    private val totalFetchMillis = AtomicLong()
    private val longestFetchMillis = AtomicLong()
    private val retainedCacheBytes = AtomicInteger()

    fun recordRead(byteCount: Int) {
        require(byteCount >= 0)
        readCalls.incrementAndGet()
        readBytes.addAndGet(byteCount.toLong())
    }

    fun recordCacheHit() {
        cacheHits.incrementAndGet()
    }

    fun recordFetchStarted() {
        httpRequests.incrementAndGet()
    }

    fun recordFetchCompleted(byteCount: Int, elapsedNanos: Long, retainedCacheBytes: Int) {
        require(byteCount > 0 && elapsedNanos >= 0 && retainedCacheBytes >= 0)
        successfulRequests.incrementAndGet()
        downloadedBytes.addAndGet(byteCount.toLong())
        val millis = elapsedNanos / 1_000_000L
        totalFetchMillis.addAndGet(millis)
        longestFetchMillis.accumulateAndGet(millis) { old, current -> maxOf(old, current) }
        this.retainedCacheBytes.set(retainedCacheBytes)
    }

    fun recordFetchFailure() {
        failedRequests.incrementAndGet()
    }

    fun snapshot() = MediaForgeRangeMetricsSnapshot(
        readCalls = readCalls.get(),
        readBytes = readBytes.get(),
        cacheHits = cacheHits.get(),
        httpRequests = httpRequests.get(),
        successfulRequests = successfulRequests.get(),
        failedRequests = failedRequests.get(),
        downloadedBytes = downloadedBytes.get(),
        totalFetchMillis = totalFetchMillis.get(),
        longestFetchMillis = longestFetchMillis.get(),
        retainedCacheBytes = retainedCacheBytes.get()
    )
}
