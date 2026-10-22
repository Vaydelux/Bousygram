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
        assertEquals(71, queue.single().fileId ?: -1)
        assertEquals("Episode 1", queue.single().title)
    }
}
