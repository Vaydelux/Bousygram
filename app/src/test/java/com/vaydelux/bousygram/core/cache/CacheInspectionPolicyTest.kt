package com.vaydelux.bousygram.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheInspectionPolicyTest {
    @Test fun rotatingPageReachesAll700TrackedIdsWith256BoundedQueries() {
        val ids = (1..700).toList()
        var cursor = 0
        val seen = mutableSetOf<Int>()
        repeat(3) {
            val page = selectCacheInspectionPage(ids, cursor, 256)
            assertEquals(256, page.ids.size)
            seen.addAll(page.ids)
            cursor = page.nextCursor
        }
        assertEquals(ids.toSet(), seen)
    }

    @Test fun pinnedBudgetAndWrapDoNotDuplicateOrExceedLimit() {
        val ids = (1..700).toList()
        val page = selectCacheInspectionPage(ids, 650, 192)
        assertEquals(192, page.ids.size)
        assertEquals(192, page.ids.distinct().size)
        assertEquals(651, page.ids.first())
        assertEquals(142, page.nextCursor)
        assertEquals(1, page.ids[50])
    }

    @Test fun changingTrackerSizeOrNoEntriesIsSafe() {
        assertEquals(CacheInspectionPage(emptyList(), 0),
            selectCacheInspectionPage(emptyList(), 100, 256))
        assertEquals(CacheInspectionPage(emptyList(), 0),
            selectCacheInspectionPage(listOf(1), 0, 0))
        val page = selectCacheInspectionPage(listOf(12, 13, 14), 998, 256)
        assertEquals(3, page.ids.size)
        assertEquals(3, page.ids.distinct().size)
        assertTrue(page.nextCursor in 0..2)
    }

    @Test fun physicalCacheSizeTakesPrecedenceOverSmallInspectionWindow() {
        val mib = TvStorageBudget.MIB
        assertEquals(450L * mib, cacheQuotaPressureBytes(450L * mib, 35L * mib))
        assertEquals(80L * mib, cacheQuotaPressureBytes(0, 80L * mib))
        assertEquals(0L, cacheQuotaPressureBytes(-5, -2))
        assertTrue(cacheQuotaPressureBytes(230L * mib, 25L * mib) > 160L * mib)
        assertFalse(cacheQuotaPressureBytes(95L * mib, 25L * mib) > 160L * mib)
    }
}
