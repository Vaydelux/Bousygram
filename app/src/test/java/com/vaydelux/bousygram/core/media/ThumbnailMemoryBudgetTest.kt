package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.core.tdlib.utils.thumbnailMemoryBudgetKiB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Budget functions are pure: no Android Bitmap or device required in JVM tests. */
class ThumbnailMemoryBudgetTest {
    @Test
    fun smallHeapKeepsPreviewMemoryBelowTwoMiB() {
        val heap = 128L * 1024L * 1024L
        assertTrue(seekPreviewCacheBudgetKiB(heap, true) <= 2_048)
        assertTrue(thumbnailMemoryBudgetKiB(heap, true, true) <= 2_048)
        assertTrue(thumbnailMemoryBudgetKiB(heap, true, false) <= 3_072)
    }

    @Test
    fun largeHeapStillHasHardCaps() {
        val heap = 1024L * 1024L * 1024L
        assertEquals(6_144, seekPreviewCacheBudgetKiB(heap, false))
        assertEquals(8_192, thumbnailMemoryBudgetKiB(heap, false, false))
        assertEquals(6_144, thumbnailMemoryBudgetKiB(heap, false, true))
    }

    @Test
    fun budgetsNeverUseZeroOrNegativeKilobytes() {
        assertTrue(seekPreviewCacheBudgetKiB(0L, true) >= 1_024)
        assertTrue(thumbnailMemoryBudgetKiB(-1L, true, true) >= 1_024)
    }
}
