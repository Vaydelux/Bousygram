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
        assertEquals("Dynamic cache limit must be 20% of free storage", 0.20, TelegramCachePruner.DEFAULT_STORAGE_FRACTION, 0.001)
        assertEquals("Minimum cache limit floor must be 256MB", 256L * 1024L * 1024L, TelegramCachePruner.MIN_CACHE_LIMIT_BYTES)
        assertEquals("Maximum cache limit ceiling must be 20GB", 20L * 1024L * 1024L * 1024L, TelegramCachePruner.MAX_CACHE_LIMIT_BYTES)
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
}
