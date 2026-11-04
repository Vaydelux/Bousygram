package com.vaydelux.bousygram.core.tdlib.prefetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusPrefetchPolicyTest {
    @Test fun partialTdlibReadMustAdvanceTowardActualHeadBudget() {
        val next = FocusPrefetchManager::nextContiguousPrefetchOffset
        assertEquals(262144L, next(0L, 262144, 2L * 1024 * 1024))
        assertEquals(524288L, next(262144L, 262144, 2L * 1024 * 1024))
        assertEquals(2L * 1024 * 1024, next(2L * 1024 * 1024 - 64L, 64, 2L * 1024 * 1024))
    }

    @Test fun zeroLengthAndOversizedReadsNeverLoopOrEscapeTheWindow() {
        val next = FocusPrefetchManager::nextContiguousPrefetchOffset
        assertNull(next(0L, 0, 2L * 1024 * 1024))
        assertNull(next(0L, -1, 2L * 1024 * 1024))
        assertNull(next(2L * 1024 * 1024, 1, 2L * 1024 * 1024))
        assertNull(next(100L, 200, 200L))
    }
}
