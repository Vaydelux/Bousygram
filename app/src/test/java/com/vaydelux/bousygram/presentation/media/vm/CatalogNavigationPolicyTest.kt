package com.vaydelux.bousygram.presentation.media.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogNavigationPolicyTest {
    @Test fun aBoundedSnapshotNeverStoresACursorForTruncatedItems() {
        assertFalse(canCacheCatalogPage(0, 120))
        assertTrue(canCacheCatalogPage(1, 120))
        assertTrue(canCacheCatalogPage(120, 120))
        assertFalse(canCacheCatalogPage(121, 120))
    }

    @Test fun quickSearchNeverFallsBackToAllChats() {
        assertEquals(91L, quickSearchScope(true, 91L, 320L))
        assertEquals(320L, quickSearchScope(false, 91L, 320L))
        assertNull(quickSearchScope(true, null, 320L))
        assertNull(quickSearchScope(false, 91L, null))
        assertNull(quickSearchScope(true, 0L, 320L))
    }

    @Test fun paginationErrorKeepsOldCursorAndAllowsRetry() {
        assertEquals(200L, resolvedPageCursor(200L, 0L, true, "network offline"))
        assertEquals(200L, resolvedPageCursor(200L, 150L, false, null))
        assertEquals(150L, resolvedPageCursor(200L, 150L, true, null))
        assertEquals(0L, resolvedPageCursor(200L, 0L, true, null))
    }

    @Test fun freshnessDoesNotMistakeClockRollbackForFreshSnapshot() {
        assertTrue(freshCatalogTimestamp(100L, 100L, 120_000L))
        assertTrue(freshCatalogTimestamp(100L, 120_100L, 120_000L))
        assertFalse(freshCatalogTimestamp(100L, 120_101L, 120_000L))
        assertFalse(freshCatalogTimestamp(100L, 99L, 120_000L))
        assertFalse(freshCatalogTimestamp(100L, 101L, -1L))
    }
}
