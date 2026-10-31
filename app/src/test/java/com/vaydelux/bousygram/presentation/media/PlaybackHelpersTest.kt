package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackHelpersTest {
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

    private fun queueItem(chatId: Long, messageId: Long, fileId: Int, title: String) = PlaybackQueueItem(
        chatId = chatId,
        messageId = messageId,
        title = title,
        fileId = fileId
    )
}
