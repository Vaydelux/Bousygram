package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtrasCachePolicyTest {
    @Test fun countsRequestAndResponseAndRoundsUp() {
        assertEquals(1, catalogCacheEntryKiB(0, 0))
        assertEquals(2, catalogCacheEntryKiB(256, 257))
        assertEquals(2, catalogCacheEntryKiB(512, 512))
        assertEquals(8_388_608, catalogCacheEntryKiB(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun deadlineCannotWrap() {
        assertEquals(1_100L, catalogDeadline(100L, 1_000L))
        assertEquals(Long.MAX_VALUE, catalogDeadline(Long.MAX_VALUE - 1, 2))
    }
}
