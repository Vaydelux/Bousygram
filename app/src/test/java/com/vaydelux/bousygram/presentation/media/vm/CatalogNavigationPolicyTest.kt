package com.vaydelux.bousygram.presentation.media.vm

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogNavigationPolicyTest {
    @Test fun aBoundedSnapshotNeverStoresACursorForTruncatedItems() {
        assertFalse(canCacheCatalogPage(0, 120))
        assertTrue(canCacheCatalogPage(1, 120))
        assertTrue(canCacheCatalogPage(120, 120))
        assertFalse(canCacheCatalogPage(121, 120))
    }

    @Test fun quickSearchNeverFallsBackToAllChats() {
        assertEquals(91L, quickSearchScope(true, 91L, 320L))
        assertEquals(320L, quickSearchScope(false, 91L, 320L))
        assertNull(quickSearchScope(true, null, 320L))
        assertNull(quickSearchScope(false, 91L, null))
        assertNull(quickSearchScope(true, 0L, 320L))
    }

    private fun media(chatId: Long, messageId: Long): MediaItem = MediaItem(
        chatId = chatId,
        messageId = messageId,
        date = 1,
        type = MediaType.Video,
        title = "Test video",
        fileId = 10
    )

    @Test fun savedMessagesNeverContainsRecentlyViewedVideosFromOtherChats() {
        val saved = media(91L, 101L)
        val recentlyViewedFromChannel = media(320L, 201L)
        val recentlyViewedFromGroup = media(450L, 301L)
        val mixedRecentIndex = listOf(recentlyViewedFromChannel, saved, recentlyViewedFromGroup)
        assertEquals(listOf(saved), savedMessagesOnly(mixedRecentIndex, 91L))
        assertTrue(savedMessagesOnly(mixedRecentIndex, null).isEmpty())
        assertTrue(savedMessagesOnly(mixedRecentIndex, 0L).isEmpty())
        assertTrue(savedMessagesOnly(mixedRecentIndex, 999L).isEmpty())
    }

    @Test fun savedMessagesSnapshotDoesNotReuseAnotherChatWhenSwitchingSources() {
        val channelVideo = media(320L, 999L)
        val savedVideo = media(91L, 1000L)
        assertEquals(listOf(savedVideo), savedMessagesOnly(listOf(channelVideo, savedVideo), 91L))
        assertEquals(listOf(channelVideo), savedMessagesOnly(listOf(channelVideo), 320L))
    }

    @Test fun continueWatchingIsScopedToTheSelectedTelegramChat() {
        // This was the remaining cross-chat navigation leak after saved-media filtering.
        assertTrue(canResumeInSource(true, 91L, 320L, 91L))
        assertFalse(canResumeInSource(true, 91L, 320L, 320L))
        assertFalse(canResumeInSource(true, 91L, 320L, 450L))
        assertFalse(canResumeInSource(true, null, 320L, 320L))
        assertFalse(canResumeInSource(true, 0L, 320L, 320L))
        assertFalse(canResumeInSource(true, 91L, 320L, null))
        assertTrue(canResumeInSource(false, 91L, 320L, 320L))
        assertFalse(canResumeInSource(false, 91L, 320L, 91L))
        assertFalse(canResumeInSource(false, 91L, null, 91L))
    }

    @Test fun paginationErrorKeepsOldCursorAndAllowsRetry() {
        assertEquals(200L, resolvedPageCursor(200L, 0L, true, "network offline"))
        assertEquals(200L, resolvedPageCursor(200L, 150L, false, null))
        assertEquals(150L, resolvedPageCursor(200L, 150L, true, null))
        assertEquals(0L, resolvedPageCursor(200L, 0L, true, null))
    }

    @Test fun freshnessDoesNotMistakeClockRollbackForFreshSnapshot() {
        assertTrue(freshCatalogTimestamp(100L, 100L, 120_000L))
        assertTrue(freshCatalogTimestamp(100L, 120_100L, 120_000L))
        assertFalse(freshCatalogTimestamp(100L, 120_101L, 120_000L))
        assertFalse(freshCatalogTimestamp(100L, 99L, 120_000L))
        assertFalse(freshCatalogTimestamp(100L, 101L, -1L))
    }
}
