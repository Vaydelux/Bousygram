package com.vaydelux.bousygram.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSearchPlanTest {
    @Test
    fun `standard page uses a small bounded metadata query budget`() {
        val plan = createMediaSearchPlan(30)
        assertEquals(30, plan.pageLimit)
        assertEquals(60, plan.documentLimit)
        assertEquals(4, plan.filterCount)
    }

    @Test
    fun `all TDLib request limits stay within 100 results`() {
        val plan = createMediaSearchPlan(500)
        assertEquals(100, plan.pageLimit)
        assertEquals(100, plan.documentLimit)
        assertEquals(4, plan.filterCount)
    }

    @Test
    fun `invalid page sizes are clamped to a valid positive query`() {
        assertEquals(1, createMediaSearchPlan(0).pageLimit)
        assertEquals(1, createMediaSearchPlan(-10).pageLimit)
    }
}
