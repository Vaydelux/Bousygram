package com.vaydelux.bousygram.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvStorageBudgetTest {
    @Test fun threeDisposableMediaCachesFitThe200MiBAllocation() {
        assertEquals(200L * 1024L * 1024L, TvStorageBudget.TOTAL_MEDIA_CACHE_BYTES)
        assertEquals(160L * TvStorageBudget.MIB, TvStorageBudget.TELEGRAM_MEDIA_CACHE_BYTES)
        assertEquals(24L * TvStorageBudget.MIB, TvStorageBudget.POSTER_DISK_CACHE_BYTES)
        assertEquals(16L * TvStorageBudget.MIB, TvStorageBudget.THUMBNAIL_DISK_CACHE_BYTES)
        assertEquals(
            TvStorageBudget.TOTAL_MEDIA_CACHE_BYTES,
            TvStorageBudget.TELEGRAM_MEDIA_CACHE_BYTES +
                TvStorageBudget.POSTER_DISK_CACHE_BYTES +
                TvStorageBudget.THUMBNAIL_DISK_CACHE_BYTES
        )
    }

    @Test fun lowStorageAndManagedLimitsCannotExpandTheDefaultCacheBudget() {
        assertEquals(
            TvStorageBudget.TELEGRAM_MEDIA_CACHE_BYTES,
            CacheManager.calculateQuota(20L * 1024L * TvStorageBudget.MIB, Long.MAX_VALUE)
        )
        assertEquals(15L * TvStorageBudget.MIB,
            CacheManager.calculateQuota(100L * TvStorageBudget.MIB, null))
        assertEquals(0L, CacheManager.calculateQuota(0L, Long.MAX_VALUE))
        assertTrue(CacheManager.calculateQuota(300L * TvStorageBudget.MIB, null) <
            TvStorageBudget.TELEGRAM_MEDIA_CACHE_BYTES)
    }
}
