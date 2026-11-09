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
    fun onlyCurrentQueueItemOwnsForegroundDownload() {
        val session = PlaybackSessionController.start(
            queue = listOf(
                com.vaydelux.bousygram.presentation.media.PlaybackQueueItem(
                    chatId = 101L, messageId = 11L, title = "First", fileId = 801
                ),
                com.vaydelux.bousygram.presentation.media.PlaybackQueueItem(
                    chatId = 101L, messageId = 12L, title = "Queued", fileId = 802
                )
            )
        )
        try {
            assertTrue(PlaybackSessionController.isCurrentPlaybackFile(801))
            assertFalse(PlaybackSessionController.isCurrentPlaybackFile(802))
            assertTrue(PlaybackSessionController.isFileActive(802))
            assertTrue(session.next())
            assertFalse(PlaybackSessionController.isCurrentPlaybackFile(801))
            assertTrue(PlaybackSessionController.isCurrentPlaybackFile(802))
        } finally {
            PlaybackSessionController.stop(session, PlaybackCacheStopReason.ViewModelStop, cancelTdLibDownload = false)
        }
        assertFalse(PlaybackSessionController.isCurrentPlaybackFile(802))
    }

    @Test
    fun queuedEpisodesRemainStreamAccessibleButDoNotAllBlockCacheEviction() {
        val session = PlaybackSessionController.start(
            queue = listOf(
                com.vaydelux.bousygram.presentation.media.PlaybackQueueItem(
                    chatId = 200L, messageId = 1L, title = "Now playing", fileId = 2201
                ),
                com.vaydelux.bousygram.presentation.media.PlaybackQueueItem(
                    chatId = 200L, messageId = 2L, title = "Already cached", fileId = 2202
                )
            )
        )
        try {
            assertTrue(PlaybackSessionController.isFileActive(2201))
            assertTrue(PlaybackSessionController.isFileActive(2202))
            assertTrue(PlaybackSessionController.isCacheProtectedPlaybackFile(2201))
            assertFalse(PlaybackSessionController.isCacheProtectedPlaybackFile(2202))
            assertFalse(PlaybackSessionController.isCacheProtectedPlaybackFile(-1))

            assertTrue(session.next())
            assertFalse(PlaybackSessionController.isCacheProtectedPlaybackFile(2201))
            assertTrue(PlaybackSessionController.isCacheProtectedPlaybackFile(2202))
            session.finishTransition()
        } finally {
            PlaybackSessionController.stop(
                session, PlaybackCacheStopReason.ViewModelStop,
                cancelTdLibDownload = false
            )
        }
        assertFalse(PlaybackSessionController.isCacheProtectedPlaybackFile(2202))
    }

    @Test
    fun diagnosticSnapshotsAreThrottledWithoutDroppingInMemoryProgress() {
        assertTrue(PlaybackSessionController.shouldPersistProgressDiagnostics(10_000L, 0L))
        assertFalse(PlaybackSessionController.shouldPersistProgressDiagnostics(10_500L, 10_000L))
        assertFalse(PlaybackSessionController.shouldPersistProgressDiagnostics(14_999L, 10_000L))
        assertTrue(PlaybackSessionController.shouldPersistProgressDiagnostics(15_000L, 10_000L))
        assertTrue(PlaybackSessionController.shouldPersistProgressDiagnostics(500L, 10_000L))
    }

    @Test
    fun cacheLeadNeverWrapsIntoNegativePlaybackTime() {
        assertEquals(
            Long.MAX_VALUE,
            PlaybackSessionController.saturatedCacheLeadPositionMs(Long.MAX_VALUE - 1_000L, 30_000L)
        )
        assertEquals(30_000L, PlaybackSessionController.saturatedCacheLeadPositionMs(-5L, 30_000L))
        assertEquals(45_000L, PlaybackSessionController.saturatedCacheLeadPositionMs(15_000L, 30_000L))
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

    @Test
    fun byteOffsetDoesNotOverflowForMultiGigabyteVideos() {
        assertEquals(
            4_000_000_000L,
            PlaybackSessionController.positionToOffset(
                totalSize = 8_000_000_000L,
                positionMs = 7_200_000L,
                durationMs = 14_400_000L
            )
        )
    }

    @Test
    fun byteOffsetWithUnknownDurationSaturatesHugePositions() {
        assertEquals(
            999_999L,
            PlaybackSessionController.positionToOffset(
                totalSize = 1_000_000L,
                positionMs = Long.MAX_VALUE,
                durationMs = 0L
            )
        )
    }

    @Test
    fun byteOffsetClampsPositionsBeyondKnownDuration() {
        assertEquals(
            999L,
            PlaybackSessionController.positionToOffset(
                totalSize = 1_000L,
                positionMs = Long.MAX_VALUE,
                durationMs = 1_000L
            )
        )
    }
}
