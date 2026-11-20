package com.vaydelux.bousygram.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbImageCachePolicyTest {
    private fun item(name: String, size: Long, time: Long) =
        TmdbImageCacheEntry(name, size, time)

    @Test fun removesOldestUnprotectedImagesToMeetDiskBudget() {
        val entries = listOf(item("p_old.jpg", 40, 10), item("p_new.jpg", 40, 30),
            item("b_new.jpg", 40, 20))
        assertEquals(listOf("p_old.jpg"), TmdbImageCachePolicy.evictions(
            entries, "p_new.jpg", maxBytes = 80, maxFiles = 5))
    }

    @Test fun preservesActiveImageWhenCountLimitIsExceeded() {
        val entries = listOf(item("p_keep.jpg", 10, 1), item("p_two.jpg", 10, 2),
            item("b_three.jpg", 10, 3))
        assertEquals(listOf("p_two.jpg", "b_three.jpg"), TmdbImageCachePolicy.evictions(
            entries, "p_keep.jpg", maxBytes = 100, maxFiles = 1))
    }

    @Test fun neverSelectsUnrecognizedFilesForDeletion() {
        val entries = listOf(item("readme.txt", 999, 1), item("../../outside.jpg", 999, 2),
            item("p_keep.jpg", 8, 3))
        assertTrue(TmdbImageCachePolicy.evictions(entries, null, maxBytes = 10).isEmpty())
    }

    @Test fun boundedBudgetHandlesCorruptLengthsWithoutOverflow() {
        val entries = listOf(item("p_a.jpg", Long.MAX_VALUE, 1),
            item("p_b.jpg", Long.MAX_VALUE, 2))
        assertEquals(listOf("p_a.jpg", "p_b.jpg"), TmdbImageCachePolicy.evictions(
            entries, null, maxBytes = 0))
    }
}
