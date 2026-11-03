package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHelpersTest {
    @Test
    fun activePlaybackExcludesPausesAndNetworkStalls() {
        val clock = ActivePlaybackClock()
        clock.reset(1_000L)
        clock.update(true, 2_000L)
        clock.update(false, 7_000L)
        assertEquals(5_000L, clock.activeDurationMs(12_000L))
        clock.update(true, 15_000L)
        assertEquals(7_000L, clock.activeDurationMs(17_000L))
        clock.update(false, 17_000L)
        assertEquals(7_000L, clock.activeDurationMs(30_000L))
        clock.reset(40_000L)
        assertEquals(0L, clock.activeDurationMs(90_000L))
    }

    @Test
    fun unknownDurationNativeEofCanAutoAdvanceAfterGenuineMediaOutput() {
        assertTrue(isCredibleNaturalPlaybackEnd(
            durationMs = 0L, positionMs = 18_000L, hasProducedMedia = true
        ))
        assertTrue(isCredibleNaturalPlaybackEnd(
            durationMs = -1L, positionMs = 18_000L, hasProducedMedia = true
        ))
        assertFalse(isCredibleNaturalPlaybackEnd(
            durationMs = 0L, positionMs = 0L, hasProducedMedia = false
        ))
        assertFalse(isCredibleNaturalPlaybackEnd(
            durationMs = 0L, positionMs = 2_500L, hasProducedMedia = true
        ))
        assertFalse(isCredibleNaturalPlaybackEnd(
            durationMs = 360_000L, positionMs = 90_000L, hasProducedMedia = true
        ))
        assertTrue(isCredibleNaturalPlaybackEnd(
            durationMs = 360_000L, positionMs = 356_000L, hasProducedMedia = true
        ))
    }

    @Test
    fun bufferingDurationUsesActualTimeAndIncludesActiveStall() {
        assertEquals(0L, measuredRebufferDurationMs(0L, 0L, 50_000L))
        assertEquals(4_200L, measuredRebufferDurationMs(1_200L, 10_000L, 13_000L))
        assertEquals(1_200L, measuredRebufferDurationMs(1_200L, 14_000L, 13_000L))
        assertEquals(1_200L, measuredRebufferDurationMs(1_200L, 0L, 13_000L))
    }

    @Test
    fun queueCarriesMetadataWithoutPreissuingStreamUrls() {
        val items = listOf(
            MediaItem(
                chatId = 101L,
                messageId = 7L,
                date = 1_700_000_000,
                type = MediaType.Video,
                title = "Episode 1",
                fileId = 71,
                durationSeconds = 1_200,
                fileSizeBytes = 20_000_000L
            ),
            MediaItem(
                chatId = 101L,
                messageId = 8L,
                date = 1_700_000_001,
                type = MediaType.Photo,
                title = "Cover",
                fileId = 72
            ),
            MediaItem(
                chatId = 101L,
                messageId = 9L,
                date = 1_700_000_002,
                type = MediaType.Video,
                title = "Episode 2",
                fileId = null
            )
        )

        val queue = buildPlaybackQueue(items)

        assertEquals(1, queue.size)
        assertNull("An inactive queue item must not hold a stream capability URL", queue.single().url)
        assertEquals(71, queue.single().fileId)
        assertEquals("Episode 1", queue.single().title)
    }

    @Test
    fun tappedMessageWinsWhenTheSameFileAppearsEarlier() {
        val first = queueItem(chatId = 1L, messageId = 10L, fileId = 71, title = "Earlier copy")
        val tapped = queueItem(chatId = 1L, messageId = 22L, fileId = 71, title = "The one I clicked")
        val queue = listOf(first, tapped)

        assertEquals(1, queueIndexForRequest(queue, chatId = 1L, messageId = 22L, fileId = 71))
        assertEquals(0, queueIndexForFileId(queue, 71))
    }

    @Test
    fun relatedTrayContainsOnlySameChatAndActualTopic() {
        val queue = listOf(
            PlaybackQueueItem(chatId = 100L, messageId = 1L, title = "Current", fileId = 1, topicId = 10L),
            PlaybackQueueItem(chatId = 100L, messageId = 2L, title = "Same topic", fileId = 2, topicId = 10L),
            PlaybackQueueItem(chatId = 100L, messageId = 3L, title = "Different topic", fileId = 3, topicId = 11L),
            PlaybackQueueItem(chatId = 200L, messageId = 4L, title = "Different chat", fileId = 4, topicId = 10L),
            PlaybackQueueItem(chatId = 100L, messageId = 5L, title = "No topic", fileId = 5, topicId = null)
        )

        assertEquals(listOf(0, 1), relatedSourceQueue(queue, 0).map { it.first })
        assertEquals(listOf(2), relatedSourceQueue(queue, 2).map { it.first })
        assertEquals(listOf(4), relatedSourceQueue(queue, 4).map { it.first })
        assertEquals(emptyList<Int>(), relatedSourceQueue(queue, -1).map { it.first })
    }

    @Test
    fun playbackRequestAndQueueRetainTelegramTopicIdentity() {
        val item = MediaItem(
            chatId = 901L,
            messageId = 19L,
            date = 1_700_000_000,
            type = MediaType.Video,
            title = "Topic media",
            fileId = 101,
            messageThreadId = 71L
        )
        val request = buildPlaybackRequest(item, null, listOf(item))

        assertEquals(71L, request.topicId)
        assertEquals(71L, request.queue.single().topicId)
        assertEquals(71L, queueItemFromRequest(request).topicId)
    }

    @Test
    fun resumeCheckpointUsesElapsedTimeAndRecordsBackwardSeeks() {
        val checkpoint = PlaybackProgressCheckpoint()
        assertFalse(checkpoint.shouldPersist(0L, 1_000L, playbackReady = true))
        assertFalse(checkpoint.shouldPersist(100_000L, 1_000L, playbackReady = false))
        assertTrue(checkpoint.shouldPersist(100_000L, 1_000L, playbackReady = true))
        assertFalse(checkpoint.shouldPersist(101_000L, 2_000L, playbackReady = true))
        assertTrue(checkpoint.shouldPersist(103_000L, 6_000L, playbackReady = true))
        // This was previously missed because 20 seconds is below the last 103 seconds.
        assertTrue(checkpoint.shouldPersist(20_000L, 6_300L, playbackReady = true))
        assertFalse(checkpoint.shouldPersist(20_000L, 9_000L, playbackReady = true))
        assertTrue(checkpoint.shouldPersist(21_000L, 11_400L, playbackReady = true))
    }

    @Test
    fun resumeCheckpointNeverClearsOldResumeAtUnseekedStartupZero() {
        val checkpoint = PlaybackProgressCheckpoint()
        assertFalse(checkpoint.shouldPersist(0L, 100L, playbackReady = true))
        assertTrue(checkpoint.shouldPersist(30_000L, 1_000L, playbackReady = true))
        checkpoint.onExplicitSeek(0L)
        assertFalse(checkpoint.shouldPersist(30_000L, 1_100L, playbackReady = true))
        assertFalse(checkpoint.shouldPersist(0L, 1_200L, playbackReady = false))
        assertTrue(checkpoint.shouldPersist(0L, 1_300L, playbackReady = true))
        assertFalse(checkpoint.shouldPersist(0L, 1_400L, playbackReady = true))
        checkpoint.reset()
        assertFalse(checkpoint.shouldPersist(0L, 1_500L, playbackReady = true))
    }

    @Test
    fun resumeCheckpointHandlesForwardSeekAndMonotonicClockReset() {
        val checkpoint = PlaybackProgressCheckpoint()
        assertTrue(checkpoint.shouldPersist(5_000L, 100_000L, playbackReady = true))
        assertTrue(checkpoint.shouldPersist(95_000L, 100_100L, playbackReady = true))
        assertFalse(checkpoint.shouldPersist(96_000L, 100_200L, playbackReady = true))
        assertTrue(checkpoint.shouldPersist(97_000L, 200L, playbackReady = true))
    }

    @Test
    fun progressUiSamplingStaysResponsiveWithoutContinuousHiddenHudWork() {
        assertEquals(1_000L, playbackUiPollingDelayMs(
            isHudVisible = false, isSeeking = false, isPreparingOrBuffering = false
        ))
        assertEquals(500L, playbackUiPollingDelayMs(
            isHudVisible = false, isSeeking = false, isPreparingOrBuffering = true
        ))
        assertEquals(250L, playbackUiPollingDelayMs(
            isHudVisible = true, isSeeking = false, isPreparingOrBuffering = false
        ))
        assertEquals(250L, playbackUiPollingDelayMs(
            isHudVisible = false, isSeeking = true, isPreparingOrBuffering = false
        ))
        assertEquals(250L, playbackUiPollingDelayMs(
            isHudVisible = true, isSeeking = true, isPreparingOrBuffering = true
        ))
    }

    private fun queueItem(chatId: Long, messageId: Long, fileId: Int, title: String) = PlaybackQueueItem(
        chatId = chatId,
        messageId = messageId,
        title = title,
        fileId = fileId
    )
}
