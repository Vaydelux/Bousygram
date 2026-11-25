package com.vaydelux.bousygram.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDirectoryProgressTest {
    @Test fun stopsAfterRepeatedNonGrowingPrefixes() {
        val progress = ChatDirectoryProgress()
        repeat(3) { assertTrue(progress.allowLoadMore(1)) }
        assertFalse(progress.allowLoadMore(1))
    }

    @Test fun actualGrowthResetsButOscillationDoesNot() {
        val progress = ChatDirectoryProgress()
        assertTrue(progress.allowLoadMore(1))
        assertTrue(progress.allowLoadMore(1))
        assertTrue(progress.allowLoadMore(2))
        assertTrue(progress.allowLoadMore(1))
        assertTrue(progress.allowLoadMore(2))
        assertFalse(progress.allowLoadMore(2))
    }
}
