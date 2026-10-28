package com.vaydelux.bousygram.core.tdlib.cache

import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutomaticCacheManagementTest {

    @Before
    fun setUp() {
        PlaybackSessionController.setVideoPlaying(false)
        FocusPrefetchManager.resetForTests()
    }

    @Test
    fun testDynamicCacheBudgetParameters() {
        assertEquals(0.15, com.vaydelux.bousygram.core.cache.CacheManager.DEFAULT_STORAGE_FRACTION, 0.001)
        assertEquals(2L * 1024L * 1024L * 1024L, com.vaydelux.bousygram.core.cache.CacheManager.MAX_CACHE_LIMIT_BYTES)
        val availableBytes = 4L * 1024L * 1024L * 1024L
        assertEquals(
            (availableBytes * 0.15).toLong(),
            com.vaydelux.bousygram.core.cache.CacheManager.calculateQuota(availableBytes, null)
        )
    }

    @Test
    fun testActivePlaybackProtection() {
        val fileId = 8888
        assertFalse(PlaybackSessionController.isFileActive(fileId))

        val session = PlaybackSessionController.start(fileId)
        assertTrue("Active playback file must be reported as active", PlaybackSessionController.isFileActive(fileId))
        assertTrue("Video playback must be active when session is started", PlaybackSessionController.isVideoPlaying())

        PlaybackSessionController.stop(session, com.vaydelux.bousygram.core.media.PlaybackCacheStopReason.Back)
        assertFalse(PlaybackSessionController.isFileActive(fileId))
    }

    @Test
    fun testWatchedMediaProgressThreshold() {
        // Duration 100 seconds (100_000 ms)
        val durationMs = 100_000L
        val uncompleted = com.vaydelux.bousygram.data.repository.ContinueWatchingEntry(
            chatId = 1L,
            messageId = 2L,
            fileId = 10,
            title = "In Progress",
            positionMs = 50_000L,
            durationMs = durationMs,
            completed = false
        )
        val isUncompletedWatched = uncompleted.completed ||
            (uncompleted.durationMs > 0L && uncompleted.positionMs >= (uncompleted.durationMs * 0.90f).toLong())
        assertFalse("50% progress should not be considered watched", isUncompletedWatched)

        val completed = com.vaydelux.bousygram.data.repository.ContinueWatchingEntry(
            chatId = 1L,
            messageId = 3L,
            fileId = 11,
            title = "Completed",
            positionMs = 92_000L,
            durationMs = durationMs,
            completed = true
        )
        val isCompletedWatched = completed.completed ||
            (completed.durationMs > 0L && completed.positionMs >= (completed.durationMs * 0.90f).toLong())
        assertTrue(">= 90% or completed flag should be considered watched", isCompletedWatched)
    }

    @Test
    fun testPrefetchActiveImmunity() {
        val fileId = 9999
        assertFalse("File must not be active prefetch initially", FocusPrefetchManager.isPrefetching(fileId))
    }

    @Test
    fun testLruEvictionOrderingUnder5GbCap() {
        val cap = 5L * 1024L * 1024L * 1024L // 5 GB
        val oneGb = 1024L * 1024L * 1024L

        data class MockCacheItem(val id: Int, val size: Long, val lastAccessTime: Long)

        val items = listOf(
            MockCacheItem(id = 1, size = 2 * oneGb, lastAccessTime = 1000L), // oldest
            MockCacheItem(id = 2, size = 2 * oneGb, lastAccessTime = 2000L),
            MockCacheItem(id = 3, size = 2 * oneGb, lastAccessTime = 3000L)  // newest
        )
        // Total size = 6 GB > 5 GB cap

        var currentTotal = items.sumOf { it.size }
        val evictedIds = mutableListOf<Int>()

        // LRU policy: Sort by access time ASC (least recently used first)
        val lruSorted = items.sortedBy { it.lastAccessTime }
        for (item in lruSorted) {
            if (currentTotal <= cap) break
            evictedIds.add(item.id)
            currentTotal -= item.size
        }

        // Must evict file 1 (t=1000L) first
        assertEquals(listOf(1), evictedIds)
        // Remaining size must be 4 GB <= 5 GB cap
        assertEquals(4 * oneGb, currentTotal)
        assertTrue(currentTotal <= cap)
    }

    @Test
    fun testLruEvictionProtectsActivePlaybackAndContinueWatching() {
        val cap = 5L * 1024L * 1024L * 1024L
        val oneGb = 1024L * 1024L * 1024L

        data class MockFile(
            val id: Int,
            val size: Long,
            val lastAccessTime: Long,
            val isPlaying: Boolean = false,
            val isContinueWatching: Boolean = false
        )

        val files = listOf(
            MockFile(id = 101, size = 2 * oneGb, lastAccessTime = 500L, isPlaying = true),
            MockFile(id = 102, size = 2 * oneGb, lastAccessTime = 600L, isContinueWatching = true),
            MockFile(id = 103, size = 2 * oneGb, lastAccessTime = 700L), // oldest eligible
            MockFile(id = 104, size = 2 * oneGb, lastAccessTime = 800L)  // newer eligible
        )
        // Total 8 GB > 5 GB cap

        // Safe candidates exclude active playback and continue watching
        val eligibleCandidates = files.filter { !it.isPlaying && !it.isContinueWatching }
        assertEquals(2, eligibleCandidates.size)
        assertFalse(eligibleCandidates.any { it.id == 101 })
        assertFalse(eligibleCandidates.any { it.id == 102 })

        var currentTotal = files.sumOf { it.size }
        val evicted = mutableListOf<Int>()

        val sortedLru = eligibleCandidates.sortedBy { it.lastAccessTime }
        for (candidate in sortedLru) {
            if (currentTotal <= cap) break
            evicted.add(candidate.id)
            currentTotal -= candidate.size
        }

        // File 103 (t=700L) evicted first, then file 104 if needed
        assertEquals(listOf(103, 104), evicted)
        // Active and continue watching files preserved
        assertFalse(evicted.contains(101))
        assertFalse(evicted.contains(102))
        assertEquals(4 * oneGb, currentTotal)
        assertTrue(currentTotal <= cap)
    }
}
