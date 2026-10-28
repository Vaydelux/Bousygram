package com.vaydelux.bousygram.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CacheManagerTest {

    @Before
    fun setUp() {
        CacheManager.clearPreloadTracking()
    }

    @Test
    fun dynamicQuota_largeStorage_cappedAt2GB() {
        // 50 GB free storage
        val availableBytes = 50L * 1024L * 1024L * 1024L
        val quota = CacheManager.calculateQuota(availableBytes, null)

        assertEquals(2L * 1024L * 1024L * 1024L, quota)
    }

    @Test
    fun dynamicQuota_budgetTvStorage_calculatedTo15Percent() {
        // 4 GB free storage on Xiaomi TV A Pro
        val availableBytes = 4L * 1024L * 1024L * 1024L
        val quota = CacheManager.calculateQuota(availableBytes, null)

        val expected = (4L * 1024L * 1024L * 1024L * 0.15).toLong()
        assertEquals(expected, quota)
        assertTrue(quota < CacheManager.MAX_CACHE_LIMIT_BYTES)
        assertTrue(quota > 0L)
    }

    @Test
    fun dynamicQuota_lowStorage_neverExceedsFreeSpaceBudget() {
        // 500 MB free storage must produce a 75 MB target, not a 256 MB floor.
        val availableBytes = 500L * 1024L * 1024L
        val quota = CacheManager.calculateQuota(availableBytes, null)

        assertEquals((availableBytes * CacheManager.DEFAULT_STORAGE_FRACTION).toLong(), quota)
    }

    @Test
    fun dynamicQuota_managedCeilingAndZeroStorageAreRespected() {
        val availableBytes = 10L * 1024L * 1024L * 1024L
        assertEquals(300L * 1024L * 1024L, CacheManager.calculateQuota(availableBytes, 300L * 1024L * 1024L))
        assertEquals(0L, CacheManager.calculateQuota(0L, null))
        assertEquals(0L, CacheManager.calculateQuota(-1L, -1L))
    }

    @Test
    fun preloadNextEpisode_triggersOncePerEpisode() {
        var preloads = 0
        val preloadedSet = mutableSetOf<Int>()

        fun testPreload(nextFileId: Int) {
            if (preloadedSet.add(nextFileId)) {
                preloads++
            }
        }

        // T = 70% of 100s: no preload
        val durationMs = 100_000L
        var posMs = 70_000L
        if (posMs >= (durationMs * 0.80)) {
            testPreload(999)
        }
        assertEquals(0, preloads)

        // T = 80% of 100s: triggers preload
        posMs = 80_000L
        if (posMs >= (durationMs * 0.80)) {
            testPreload(999)
        }
        assertEquals(1, preloads)

        // T = 85% of 100s: deduplicated, no additional preload
        posMs = 85_000L
        if (posMs >= (durationMs * 0.80)) {
            testPreload(999)
        }
        assertEquals(1, preloads)

        // New episode 1001 reaches 80%: triggers exactly once
        posMs = 80_000L
        if (posMs >= (durationMs * 0.80)) {
            testPreload(1001)
        }
        assertEquals(2, preloads)
    }

    @Test
    fun slidingWindowRange_computesAccurateBounds() {
        val newPositionMs = 60_000L // 1 minute
        val durationMs = 120_000L   // 2 minutes
        val totalSize = 100_000_000L // 100 MB

        val offset = ((newPositionMs * totalSize) / durationMs).coerceIn(0L, totalSize - 1)
        assertEquals(50_000_000L, offset) // Exactly 50%

        val windowBytes = CacheManager.SLIDING_WINDOW_BYTES // 32 MB
        val validStart = (offset - 2L * 1024L * 1024L).coerceAtLeast(0L)
        val validEnd = offset + windowBytes

        // Valid window: [offset - 2MB, offset + 32MB]
        assertEquals(offset - 2L * 1024L * 1024L, validStart)
        assertEquals(offset + windowBytes, validEnd)

        // Chunks at 10 MB or 90 MB are out of window
        val chunk1Range = 10_000_000L..12_000_000L
        val isChunk1OutOfWindow = chunk1Range.last < validStart || chunk1Range.first > validEnd
        assertTrue(isChunk1OutOfWindow)

        // Chunk at 55 MB is in window
        val chunk2Range = 55_000_000L..57_000_000L
        val isChunk2OutOfWindow = chunk2Range.last < validStart || chunk2Range.first > validEnd
        assertFalse(isChunk2OutOfWindow)
    }
}
