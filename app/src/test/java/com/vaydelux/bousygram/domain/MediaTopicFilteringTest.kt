package com.vaydelux.bousygram.domain

import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import com.vaydelux.bousygram.domain.model.MediaUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTopicFilteringTest {

    private fun createItem(
        messageId: Long,
        title: String,
        threadId: Long?,
        seriesTitle: String? = null
    ): MediaItem {
        return MediaItem(
            chatId = -1001234567890L,
            messageId = messageId,
            date = 1700000000 + messageId.toInt(),
            type = MediaType.Video,
            title = title,
            fileId = messageId.toInt(),
            messageThreadId = threadId,
            seriesTitle = seriesTitle
        )
    }

    private val sampleTopics = listOf(
        ForumTopicChannel(id = 1, chatId = -1001234567890L, name = "General", isGeneral = true),
        ForumTopicChannel(id = 100, chatId = -1001234567890L, name = "Action Movies"),
        ForumTopicChannel(id = 200, chatId = -1001234567890L, name = "Anime & Cartoons")
    )

    private val sampleItems = listOf(
        createItem(1, "General Welcome Video", threadId = 1L),
        createItem(2, "Old Untagged General Video", threadId = null),
        createItem(3, "Mad Max Fury Road", threadId = 100L),
        createItem(4, "John Wick 4", threadId = 100L),
        createItem(5, "Attack on Titan S01E01", threadId = 200L, seriesTitle = "Attack on Titan"),
        createItem(6, "Attack on Titan S01E02", threadId = 200L, seriesTitle = "Attack on Titan")
    )

    @Test
    fun allChannelsReturnsAllItemsWithoutTopicFilter() {
        val state = MediaUiState(
            items = sampleItems,
            availableTopics = sampleTopics,
            selectedTopicId = null // All channels
        )

        assertEquals(6, state.filteredAndSortedItems.size)
    }

    @Test
    fun specificTopicFiltersOnlyMatchingItems() {
        val state = MediaUiState(
            items = sampleItems,
            availableTopics = sampleTopics,
            selectedTopicId = 100 // Action Movies
        )

        val filtered = state.filteredAndSortedItems
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.messageThreadId == 100L })
        assertEquals("John Wick 4", filtered[0].title)
        assertEquals("Mad Max Fury Road", filtered[1].title)
        // Topic name should be resolved from availableTopics
        assertEquals("Action Movies", filtered[0].topicName)
    }

    @Test
    fun generalTopicIncludesExplicitAndFallbackGeneralItems() {
        val state = MediaUiState(
            items = sampleItems,
            availableTopics = sampleTopics,
            selectedTopicId = 1 // General topic
        )

        val filtered = state.filteredAndSortedItems
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.title == "General Welcome Video" })
        assertTrue(filtered.any { it.title == "Old Untagged General Video" })
    }

    @Test
    fun topicGroupsResolvesHumanReadableTopicNames() {
        val state = MediaUiState(
            items = sampleItems,
            availableTopics = sampleTopics,
            selectedTopicId = null
        )

        val groups = state.topicGroups
        assertTrue(groups.containsKey("Action Movies"))
        assertTrue(groups.containsKey("Anime & Cartoons"))
        assertTrue(groups.containsKey("General"))
        assertEquals(2, groups["Action Movies"]?.size)
        assertEquals(2, groups["Anime & Cartoons"]?.size)
    }

    @Test
    fun seriesGroupsRespectsTopicFiltering() {
        val actionState = MediaUiState(
            items = sampleItems,
            availableTopics = sampleTopics,
            selectedTopicId = 100 // Action Movies (no series)
        )
        assertTrue(actionState.seriesGroups.isEmpty())

        val animeState = MediaUiState(
            items = sampleItems,
            availableTopics = sampleTopics,
            selectedTopicId = 200 // Anime (contains Attack on Titan)
        )
        assertEquals(1, animeState.seriesGroups.size)
        assertEquals("Attack on Titan", animeState.seriesGroups[0].title)
        assertEquals(2, animeState.seriesGroups[0].episodes.size)
    }
}
