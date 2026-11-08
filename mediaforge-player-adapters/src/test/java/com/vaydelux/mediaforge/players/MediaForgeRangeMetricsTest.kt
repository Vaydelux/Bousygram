package com.vaydelux.mediaforge.players

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaForgeRangeMetricsTest {
    @Test fun emptySourceProducesZeroStatsWithoutExposingUrls() {
        val stats = MediaForgeRangeMetrics().snapshot()
        assertEquals(0L, stats.httpRequests)
        assertEquals(0L, stats.downloadedBytes)
        assertEquals(0, stats.cacheHitPercent)
        assertEquals(0, stats.retainedCacheBytes)
        assertTrue(stats.toString().contains("downloadedBytes=0"))
    }

    @Test fun recordsNetworkReadsAndBoundedCacheSnapshots() {
        val metrics = MediaForgeRangeMetrics()
        metrics.recordFetchStarted()
        metrics.recordFetchCompleted(256 * 1024, 25_000_000L, 256 * 1024)
        metrics.recordCacheHit()
        metrics.recordCacheHit()
        metrics.recordRead(128 * 1024)
        metrics.recordRead(10)
        val stats = metrics.snapshot()
        assertEquals(1L, stats.httpRequests)
        assertEquals(1L, stats.successfulRequests)
        assertEquals(0L, stats.failedRequests)
        assertEquals(2L, stats.cacheHits)
        assertEquals(66, stats.cacheHitPercent)
        assertEquals(262_144L, stats.downloadedBytes)
        assertEquals(131_082L, stats.readBytes)
        assertEquals(2L, stats.readCalls)
        assertEquals(25L, stats.longestFetchMillis)
        assertEquals(25L, stats.totalFetchMillis)
        assertEquals(256 * 1024, stats.retainedCacheBytes)
    }

    @Test fun failedRequestsAndFetchTimesNeverEraseSuccessfulMeasurements() {
        val metrics = MediaForgeRangeMetrics()
        metrics.recordFetchStarted()
        metrics.recordFetchFailure()
        metrics.recordFetchStarted()
        metrics.recordFetchCompleted(1, 1_000_000L, 1)
        metrics.recordFetchStarted()
        metrics.recordFetchCompleted(10, 50_000_000L, 11)
        val stats = metrics.snapshot()
        assertEquals(3L, stats.httpRequests)
        assertEquals(1L, stats.failedRequests)
        assertEquals(2L, stats.successfulRequests)
        assertEquals(11L, stats.downloadedBytes)
        assertEquals(51L, stats.totalFetchMillis)
        assertEquals(50L, stats.longestFetchMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeReadBytesRejected() { MediaForgeRangeMetrics().recordRead(-1) }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedNegativeCacheRejected() {
        MediaForgeRangeMetrics().recordFetchCompleted(100, 100L, -1)
    }
}
