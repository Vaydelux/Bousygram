package com.vaydelux.bousygram.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMemoryTrimTest {
    @Test fun uiHiddenDoesNotFalselyCountAsCriticalMemoryPressure() {
        assertEquals(MediaMemoryTrim.NONE, mediaMemoryTrim(20))
        assertEquals(MediaMemoryTrim.NONE, mediaMemoryTrim(0))
    }

    @Test fun runningPressureAndBackgroundAreHandledByCategory() {
        assertEquals(MediaMemoryTrim.MODERATE, mediaMemoryTrim(5))
        assertEquals(MediaMemoryTrim.MODERATE, mediaMemoryTrim(10))
        assertEquals(MediaMemoryTrim.CRITICAL, mediaMemoryTrim(15))
        assertEquals(MediaMemoryTrim.MODERATE, mediaMemoryTrim(40))
        assertEquals(MediaMemoryTrim.MODERATE, mediaMemoryTrim(60))
        assertEquals(MediaMemoryTrim.CRITICAL, mediaMemoryTrim(80))
    }

    @Test fun criticalOffscreenTrimKeepsOnlyOneRecentNavigationAnchor() {
        val normal = offscreenCatalogBudget(MediaMemoryTrim.NONE)
        val moderate = offscreenCatalogBudget(MediaMemoryTrim.MODERATE)
        val critical = offscreenCatalogBudget(MediaMemoryTrim.CRITICAL)
        assertTrue(normal.snapshots > moderate.snapshots)
        assertEquals(0, critical.snapshots)
        assertEquals(0, critical.topics)
        assertEquals(1, critical.browsingPositions)
        assertEquals(1, critical.focusedTopics)
    }
}
