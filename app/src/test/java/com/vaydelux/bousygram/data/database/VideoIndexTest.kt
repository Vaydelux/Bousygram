package com.vaydelux.bousygram.data.database

import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoIndexTest {

    @Test
    fun testMediaItemToVideoEntityMapping() {
        val mediaItem = MediaItem(
            chatId = -100123456789L,
            messageId = 42L,
            date = 1700000000,
            type = MediaType.Video,
            title = "Breaking Bad S02E05 1080p",
            fileId = 999,
            miniThumbnailBytes = byteArrayOf(1, 2, 3, 4),
            durationSeconds = 3600,
            fileSizeBytes = 1024L * 1024L * 500L,
            messageThreadId = 77L,
            seasonNumber = 2,
            episodeNumber = 5
        )

        val entity = mediaItem.toVideoEntity()

        assertEquals(-100123456789L, entity.chatId)
        assertEquals(42L, entity.messageId)
        assertEquals(77L, entity.topicId)
        assertEquals(999, entity.fileId)
        assertEquals("Breaking Bad S02E05 1080p", entity.title)
        assertEquals(3600L, entity.duration)
        assertEquals(1024L * 1024L * 500L, entity.size)
        assertEquals(2, entity.season)
        assertEquals(5, entity.episode)
        assertNotNull(entity.minithumbnail)
        assertEquals(4, entity.minithumbnail!!.size)
        assertTrue(entity.lastSeen > 0L)
    }

    @Test
    fun testMediaItemToVideoEntityParsesSeasonAndEpisodeWhenMissing() {
        val mediaItem = MediaItem(
            chatId = -100987654321L,
            messageId = 100L,
            date = 1700000000,
            type = MediaType.Video,
            title = "Stranger Things S04E08.mkv",
            fileId = 888,
            durationSeconds = 4200,
            fileSizeBytes = 2_000_000_000L,
            seasonNumber = null,
            episodeNumber = null
        )

        val entity = mediaItem.toVideoEntity()

        assertEquals(4, entity.season)
        assertEquals(8, entity.episode)
    }

    @Test
    fun testVideoEntityToMediaItemMapping() {
        val entity = VideoEntity(
            -100123456789L,
            12L,
            55L,
            777,
            "Better Call Saul S01E03",
            3000L,
            1_500_000_000L,
            byteArrayOf(9, 8, 7),
            1,
            3,
            1710000000000L
        )

        val item = entity.toMediaItem()

        assertEquals(-100123456789L, item.chatId)
        assertEquals(55L, item.messageId)
        assertEquals(12L, item.messageThreadId)
        assertEquals(777, item.fileId)
        assertEquals("Better Call Saul S01E03", item.title)
        assertEquals(3000, item.durationSeconds)
        assertEquals(3000_000L, item.trustedDurationMs)
        assertEquals(1_500_000_000L, item.fileSizeBytes)
        assertEquals(1, item.seasonNumber)
        assertEquals(3, item.episodeNumber)
        assertEquals(MediaType.Video, item.type)
        assertEquals("Better Call Saul", item.seriesTitle)
        assertNotNull(item.miniThumbnailBytes)
    }

    @Test
    fun testPlaybackActivePausesIndexing() = runTest {
        try {
            PlaybackSessionController.setVideoPlaying(false)
            assertFalse(PlaybackSessionController.isVideoPlaying())

            var indexingExecuted = false

            // Set video playing
            PlaybackSessionController.setVideoPlaying(true)
            assertTrue(PlaybackSessionController.isVideoPlaying())

            val job = launch {
                while (PlaybackSessionController.isVideoPlaying()) {
                    delay(1000L)
                }
                indexingExecuted = true
            }

            runCurrent()
            advanceTimeBy(3000L)
            runCurrent()

            // Indexing should NOT have executed because video is playing
            assertFalse(indexingExecuted)

            // Video stops playing
            PlaybackSessionController.setVideoPlaying(false)
            assertFalse(PlaybackSessionController.isVideoPlaying())

            advanceTimeBy(1001L)
            runCurrent()

            // Indexing should now have executed!
            assertTrue(indexingExecuted)
            job.cancel()
        } finally {
            PlaybackSessionController.setVideoPlaying(false)
        }
    }

    @Test
    fun testRecentSearchEntityToDomainMapping() {
        val entity = RecentSearchEntity(
            "dune part two",
            "Dune Part Two",
            -100777888999L,
            "Cinema 4K",
            14,
            1720000000000L
        )
        val model = entity.toDomain()
        assertEquals("dune part two", model.normalizedQuery)
        assertEquals("Dune Part Two", model.query)
        assertEquals(-100777888999L, model.chatId)
        assertEquals("Cinema 4K", model.chatTitle)
        assertEquals(14, model.resultCount)
        assertEquals(1720000000000L, model.timestampMs)
    }

    @Test
    fun testMediaUiStateQuickSearchQueryFiltering() {
        val items = listOf(
            MediaItem(
                chatId = -1001L,
                messageId = 1L,
                date = 1700000010,
                type = MediaType.Video,
                title = "Cyberpunk Edgerunners S01E01 1080p",
                fileId = 101,
                durationSeconds = 1440
            ),
            MediaItem(
                chatId = -1001L,
                messageId = 2L,
                date = 1700000020,
                type = MediaType.Video,
                title = "Interstellar IMAX Remux",
                fileId = 102,
                durationSeconds = 10140
            ),
            MediaItem(
                chatId = -1001L,
                messageId = 3L,
                date = 1700000030,
                type = MediaType.Video,
                title = "Cyberpunk Edgerunners S01E02 1080p",
                fileId = 103,
                durationSeconds = 1480
            )
        )
        val unfiltered = com.vaydelux.bousygram.domain.model.MediaUiState(
            items = items,
            quickSearchQuery = ""
        )
        assertEquals(3, unfiltered.filteredAndSortedItems.size)

        val filtered = unfiltered.copy(quickSearchQuery = "cyberpunk")
        assertEquals(2, filtered.filteredAndSortedItems.size)
        assertTrue(filtered.filteredAndSortedItems.all { it.title.contains("Cyberpunk", ignoreCase = true) })
    }
}
