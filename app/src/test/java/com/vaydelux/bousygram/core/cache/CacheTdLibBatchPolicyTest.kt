package com.vaydelux.bousygram.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheTdLibBatchPolicyTest {
    @Test fun boundedRequestsCoverEntire256FileInspectionWindow() {
        val ids = (1..256).toList()
        val batches = cacheTdLibRequestBatches(ids)
        assertEquals(8, batches.size)
        assertTrue(batches.all { it.size in 1..32 })
        assertEquals(ids, batches.flatten())
    }

    @Test fun smallerAndEmptyLookupsRemainSafe() {
        assertEquals(emptyList<List<Int>>(), cacheTdLibRequestBatches(emptyList()))
        val batches = cacheTdLibRequestBatches((1..65).toList(), 16)
        assertEquals(listOf(16, 16, 16, 16, 1), batches.map { it.size })
        assertEquals((1..65).toList(), batches.flatten())
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroInFlightIsRejected() { cacheTdLibRequestBatches(listOf(1), 0) }

    @Test(expected = IllegalArgumentException::class)
    fun exceedingAllowedInflightIsRejected() { cacheTdLibRequestBatches(listOf(1), 33) }

    @Test fun oneTotalTimeoutNeverResetsPerBatch() {
        val begin = 1_000_000_000L
        val timeoutMs = 4_000L
        assertEquals(4_000_000_000L, cacheTdLibRemainingNanos(begin, begin, timeoutMs))
        assertEquals(2_000_000_000L, cacheTdLibRemainingNanos(
            begin, begin + 2_000_000_000L, timeoutMs
        ))
        assertEquals(0L, cacheTdLibRemainingNanos(begin, begin + 4_000_000_000L, timeoutMs))
        assertEquals(0L, cacheTdLibRemainingNanos(begin, begin + 5_000_000_000L, timeoutMs))
        assertEquals(0L, cacheTdLibRemainingNanos(begin, begin - 1, timeoutMs))
    }
}
