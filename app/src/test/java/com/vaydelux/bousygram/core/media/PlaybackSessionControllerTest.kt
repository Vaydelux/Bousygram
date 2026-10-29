package com.vaydelux.bousygram.core.media

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionControllerTest {
    @Test
    fun telegramPendingRetriesThenUsesCacheFirst() {
        val session = PlaybackSessionController.start(fileId = 40)
        val error = PlaybackException(
            "Telegram is still caching range fileId=40 offset=0",
            null,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        )

        assertEquals(
            PlaybackRetryDecision.RetrySameQuality,
            PlaybackSessionController.recordError(session, error, retryCount = 0)
        )
        assertEquals(
            PlaybackRetryDecision.CacheFirstRetry,
            PlaybackSessionController.recordError(session, error, retryCount = 1)
        )
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.ViewModelStop)
    }

    @Test
    fun activeFileRegistryProtectsFilesUntilAllSessionsStop() {
        val first = PlaybackSessionController.start(fileId = 104)
        val second = PlaybackSessionController.start(fileId = 104)
        assertTrue(PlaybackSessionController.isFileActive(104))

        PlaybackSessionController.stop(first, PlaybackCacheStopReason.ViewModelStop)
        assertTrue(PlaybackSessionController.isFileActive(104))

        PlaybackSessionController.stop(second, PlaybackCacheStopReason.ViewModelStop)
        assertFalse(PlaybackSessionController.isFileActive(104))
        // Repeated stop calls must not corrupt active-session tracking.
        PlaybackSessionController.stop(second, PlaybackCacheStopReason.Dispose)
        assertFalse(PlaybackSessionController.isFileActive(104))
    }

    @Test
    fun queueNavigationKeepsSameSessionAliveUntilExplicitStop() {
        val session = PlaybackSessionController.start(
            queue = listOf(
                com.vaydelux.bousygram.presentation.media.PlaybackQueueItem(
                    chatId = 101L, messageId = 11L, title = "Episode 1", fileId = 301
                ),
                com.vaydelux.bousygram.presentation.media.PlaybackQueueItem(
                    chatId = 101L, messageId = 12L, title = "Episode 2", fileId = 302
                )
            )
        )
        assertTrue(session.next())
        assertFalse(session.isStopped())
        assertEquals(302, session.fileId)
        session.finishTransition()
        assertTrue(session.previous())
        assertFalse(session.isStopped())
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Back)
        assertTrue(session.isStopped())
    }

    @Test
    fun byteOffsetUsesDurationWhenKnown() {
        assertEquals(
            500_000L,
            PlaybackSessionController.positionToOffset(
                totalSize = 1_000_000L,
                positionMs = 30_000L,
                durationMs = 60_000L
            )
        )
    }

    @Test
    fun byteOffsetFallsBackToConservativeEstimateWhenDurationUnknown() {
        assertEquals(
            5_120_000L,
            PlaybackSessionController.positionToOffset(
                totalSize = 100_000_000L,
                positionMs = 10_000L,
                durationMs = 0L
            )
        )
    }

    @Test
    fun byteOffsetClampsNearEndOfFile() {
        assertEquals(
            999L,
            PlaybackSessionController.positionToOffset(
                totalSize = 1_000L,
                positionMs = 120_000L,
                durationMs = 60_000L
            )
        )
    }
}
