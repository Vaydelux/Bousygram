package com.vaydelux.bousygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Older serialized MediaItem snapshots may contain deprecated TMDB fields.
 * Telegram browsing and series grouping must ignore those values entirely.
 */
class TelegramLocalMetadataOnlyTest {
    private val item = MediaItem(
        chatId = -100L,
        messageId = 123L,
        date = 1,
        type = MediaType.Video,
        title = "Actual.Series.S01E02.mkv",
        fileId = 77,
        fileName = "Actual.Series.S01E02.mkv",
        seriesTitle = "Actual Series",
        seasonNumber = 1,
        episodeNumber = 2,
        tmdbTitle = "Unrelated External Film",
        tmdbPosterPath = "https://image.tmdb.org/t/p/w500/wrong.jpg",
        tmdbOverview = "Outdated third-party information"
    )

    @Test
    fun legacyExternalMatchCannotRenameTelegramSeries() {
        val group = groupMediaIntoSeries(listOf(item)).single()
        assertEquals("Actual Series", group.title)
        assertEquals(item.fileId, group.episodes.single().fileId)
        assertEquals(item.fileName, group.episodes.single().fileName)
    }

    @Test
    fun telegramSearchIgnoresLegacyExternalTitle() {
        val stale = MediaUiState(
            items = listOf(item),
            quickSearchQuery = "Unrelated External Film"
        )
        assertTrue(stale.filteredAndSortedItems.isEmpty())

        val original = stale.copy(quickSearchQuery = "Actual.Series")
        assertEquals(1, original.filteredAndSortedItems.size)
        assertEquals(77, original.filteredAndSortedItems.single().fileId)
    }
}
