package com.vaydelux.bousygram.domain

import com.vaydelux.bousygram.data.database.VideoEntity
import com.vaydelux.bousygram.data.database.toMediaItem
import com.vaydelux.bousygram.data.database.toVideoEntity
import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import com.vaydelux.bousygram.domain.model.MediaUiState
import com.vaydelux.bousygram.domain.model.groupMediaIntoSeries
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests verifying the Telegram forum groups hierarchy:
 * Chat -> Topic -> Series -> Episodes
 * - TDLib forum topic APIs & video filtering by message thread ID
 * - Room video index topic ID storage and retrieval
 * - Grouping of episodes into series within topics
 * - Episode ordering (season, episode, date)
 */
class ForumTopicSeriesHierarchyTest {

    private val forumChatId = -1001987654321L

    private val fakeTopics = listOf(
        ForumTopicChannel(
            id = 1,
            chatId = forumChatId,
            name = "General",
            isGeneral = true,
            isPinned = true
        ),
        ForumTopicChannel(
            id = 10,
            chatId = forumChatId,
            name = "Breaking Bad",
            isGeneral = false
        ),
        ForumTopicChannel(
            id = 20,
            chatId = forumChatId,
            name = "Sci-Fi & Fantasy",
            isGeneral = false
        ),
        ForumTopicChannel(
            id = 30,
            chatId = forumChatId,
            name = "Documentaries",
            isGeneral = false
        )
    )

    private fun fakeVideo(
        messageId: Long,
        title: String,
        threadId: Long?,
        seriesTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        date: Int = 1700000000 + messageId.toInt()
    ): MediaItem {
        return MediaItem(
            chatId = forumChatId,
            messageId = messageId,
            date = date,
            type = MediaType.Video,
            title = title,
            fileId = (1000 + messageId).toInt(),
            messageThreadId = threadId,
            seriesTitle = seriesTitle,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }

    @Test
    fun testChatToTopicFiltering() {
        val fakeVideos = listOf(
            fakeVideo(1, "Welcome video", threadId = 1L),
            fakeVideo(2, "Breaking Bad S01E01", threadId = 10L, seriesTitle = "Breaking Bad", seasonNumber = 1, episodeNumber = 1),
            fakeVideo(3, "Breaking Bad S01E02", threadId = 10L, seriesTitle = "Breaking Bad", seasonNumber = 1, episodeNumber = 2),
            fakeVideo(4, "Dune 2", threadId = 20L),
            fakeVideo(5, "Planet Earth Episode 1", threadId = 30L, episodeNumber = 1)
        )

        val stateTopic10 = MediaUiState(
            items = fakeVideos,
            availableTopics = fakeTopics,
            selectedTopicId = 10
        )

        val filteredTopic10 = stateTopic10.filteredAndSortedItems
        assertEquals(2, filteredTopic10.size)
        assertTrue(filteredTopic10.all { it.messageThreadId == 10L })
        assertEquals("Breaking Bad", filteredTopic10[0].topicName)
    }

    @Test
    fun testTopicToSeriesToEpisodesHierarchy() {
        val fakeVideos = listOf(
            fakeVideo(101, "Breaking Bad S01E02", threadId = 10L, seriesTitle = "Breaking Bad", seasonNumber = 1, episodeNumber = 2, date = 200),
            fakeVideo(102, "Breaking Bad S01E01", threadId = 10L, seriesTitle = "Breaking Bad", seasonNumber = 1, episodeNumber = 1, date = 100),
            fakeVideo(103, "Breaking Bad S02E01", threadId = 10L, seriesTitle = "Breaking Bad", seasonNumber = 2, episodeNumber = 1, date = 300)
        )

        val state = MediaUiState(
            items = fakeVideos,
            availableTopics = fakeTopics,
            selectedTopicId = 10
        )

        // Topic -> Series
        val series = state.seriesGroups
        assertEquals(1, series.size)
        val breakingBad = series.first()
        assertEquals("Breaking Bad", breakingBad.title)
        assertEquals(2, breakingBad.totalSeasons)

        // Series -> Episodes (must be sorted: S01E01, S01E02, S02E01)
        val episodes = breakingBad.episodes
        assertEquals(3, episodes.size)
        assertEquals(1, episodes[0].seasonNumber)
        assertEquals(1, episodes[0].episodeNumber)
        assertEquals(1, episodes[1].seasonNumber)
        assertEquals(2, episodes[1].episodeNumber)
        assertEquals(2, episodes[2].seasonNumber)
        assertEquals(1, episodes[2].episodeNumber)
    }

    @Test
    fun testEpisodeNumberOnlyFallsBackToTopicNameAsSeriesTitle() {
        // Files in topic "Breaking Bad" (id = 10) have episode numbering but no show title prefix in filename
        val fakeVideos = listOf(
            fakeVideo(201, "Episode 2 - Cat's in the Bag", threadId = 10L, episodeNumber = 2, seasonNumber = 1),
            fakeVideo(202, "Episode 1 - Pilot", threadId = 10L, episodeNumber = 1, seasonNumber = 1)
        )

        val state = MediaUiState(
            items = fakeVideos,
            availableTopics = fakeTopics,
            selectedTopicId = 10
        )

        val series = state.seriesGroups
        assertEquals(1, series.size)
        assertEquals("Breaking Bad", series.first().title)
        assertEquals(2, series.first().episodes.size)
        assertEquals(1, series.first().episodes[0].episodeNumber)
        assertEquals(2, series.first().episodes[1].episodeNumber)
    }

    @Test
    fun testMultipleSeriesWithinSingleTopic() {
        // Sci-Fi topic (id = 20) contains both "The Expanse" and "Foundation"
        val fakeVideos = listOf(
            fakeVideo(301, "The Expanse S01E01", threadId = 20L, seriesTitle = "The Expanse", seasonNumber = 1, episodeNumber = 1),
            fakeVideo(302, "Foundation S01E01", threadId = 20L, seriesTitle = "Foundation", seasonNumber = 1, episodeNumber = 1),
            fakeVideo(303, "The Expanse S01E02", threadId = 20L, seriesTitle = "The Expanse", seasonNumber = 1, episodeNumber = 2),
            fakeVideo(304, "Interstellar Movie", threadId = 20L) // standalone movie, not a series
        )

        val state = MediaUiState(
            items = fakeVideos,
            availableTopics = fakeTopics,
            selectedTopicId = 20
        )

        val series = state.seriesGroups
        assertEquals(2, series.size)
        assertEquals("Foundation", series[0].title)
        assertEquals(1, series[0].episodes.size)
        assertEquals("The Expanse", series[1].title)
        assertEquals(2, series[1].episodes.size)
    }

    @Test
    fun testTopicSeriesGroupsMap() {
        val fakeVideos = listOf(
            fakeVideo(401, "BB S01E01", threadId = 10L, seriesTitle = "Breaking Bad", seasonNumber = 1, episodeNumber = 1),
            fakeVideo(402, "Foundation S01E01", threadId = 20L, seriesTitle = "Foundation", seasonNumber = 1, episodeNumber = 1)
        )

        val state = MediaUiState(
            items = fakeVideos,
            availableTopics = fakeTopics,
            selectedTopicId = null
        )

        val topicSeries = state.topicSeriesGroups
        assertTrue(topicSeries.containsKey("Breaking Bad"))
        assertTrue(topicSeries.containsKey("Sci-Fi & Fantasy"))
        assertEquals("Breaking Bad", topicSeries["Breaking Bad"]?.first()?.title)
        assertEquals("Foundation", topicSeries["Sci-Fi & Fantasy"]?.first()?.title)
    }

    @Test
    fun testTopicIdStoredInRoomIndexAndRetrieved() {
        val item = MediaItem(
            chatId = forumChatId,
            messageId = 555L,
            date = 1700000000,
            type = MediaType.Video,
            title = "Westworld S01E01",
            fileId = 999,
            durationSeconds = 3600,
            fileSizeBytes = 2_000_000_000L,
            seriesTitle = "Westworld",
            seasonNumber = 1,
            episodeNumber = 1,
            messageThreadId = 777L
        )

        // Convert to Room VideoEntity
        val entity: VideoEntity = item.toVideoEntity()
        assertEquals(forumChatId, entity.chatId)
        assertEquals(777L, entity.topicId)
        assertEquals(555L, entity.messageId)
        assertEquals(1, entity.season)
        assertEquals(1, entity.episode)

        // Convert back from VideoEntity to MediaItem
        val restoredItem = entity.toMediaItem()
        assertEquals(forumChatId, restoredItem.chatId)
        assertEquals(777L, restoredItem.messageThreadId)
        assertEquals(555L, restoredItem.messageId)
        assertEquals("Westworld", restoredItem.seriesTitle)
        assertEquals(1, restoredItem.seasonNumber)
        assertEquals(1, restoredItem.episodeNumber)
    }

    @Test
    fun testTdLibForumTopicSearchRequestStructure() {
        val topicId = 10
        val topic = TdApi.MessageTopicForum(topicId)
        val filter = TdApi.SearchMessagesFilterVideo()
        val search = TdApi.SearchChatMessages(
            forumChatId,
            topic,
            "",
            null,
            0L,
            0,
            50,
            filter
        )

        assertEquals(forumChatId, search.chatId)
        assertNotNull(search.topicId)
        assertTrue(search.topicId is TdApi.MessageTopicForum)
        assertEquals(10, (search.topicId as TdApi.MessageTopicForum).forumTopicId)
        assertTrue(search.filter is TdApi.SearchMessagesFilterVideo)
    }
}
