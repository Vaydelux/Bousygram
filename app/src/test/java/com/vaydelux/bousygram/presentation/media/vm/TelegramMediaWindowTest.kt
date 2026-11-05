package com.vaydelux.bousygram.presentation.media.vm

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramMediaWindowTest {
    private fun media(chatId: Long, messageId: Long) = MediaItem(
        chatId = chatId, messageId = messageId, date = 1,
        type = MediaType.Video, title = "Fixture " + messageId, fileId = 10
    )

    @Test fun lowRamTraversalStaysBoundedAcross100Pages() {
        var retained = emptyList<MediaItem>()
        var discarded = 0
        for (page in 0 until 100) {
            val next = (page * 30 + 1..page * 30 + 30).map { media(11L, it.toLong()) }
            val merged = TelegramMediaWindow.merge(retained, next, 120)
            retained = merged.items
            discarded += merged.removedFromFront
            assertTrue(retained.size <= 120)
        }
        assertEquals(120, retained.size)
        assertEquals(2880, discarded)
        assertEquals(2881L, retained.first().messageId)
        assertEquals(3000L, retained.last().messageId)
    }

    @Test fun standardTraversalStaysBoundedAcross300Pages() {
        var retained = emptyList<MediaItem>()
        for (page in 0 until 300) {
            val next = (page * 30 + 1..page * 30 + 30).map { media(11L, it.toLong()) }
            retained = TelegramMediaWindow.merge(retained, next, 240).items
            assertTrue(retained.size <= 240)
        }
        assertEquals(8761L, retained.first().messageId)
        assertEquals(9000L, retained.last().messageId)
    }

    @Test fun overlapAndSameMessageIdInOtherChatRemainIndependent() {
        val existing = (1..120).map { media(11L, it.toLong()) }
        val overlap = (115..135).map { media(11L, it.toLong()) } + media(22L, 120L)
        val merged = TelegramMediaWindow.merge(existing, overlap, 120)
        assertEquals(120, merged.items.size)
        assertEquals(16, merged.removedFromFront)
        assertEquals(17L, merged.items.first().messageId)
        assertTrue(merged.items.any { it.chatId == 22L && it.messageId == 120L })
        assertEquals(120, merged.items.distinctBy { it.chatId to it.messageId }.size)
    }

    @Test fun emptyPageAndFreshRestartDoNotDropAnything() {
        val retained = (1..120).map { media(11L, it.toLong()) }
        assertEquals(retained, TelegramMediaWindow.merge(retained, emptyList(), 120).items)
        val restart = TelegramMediaWindow.merge(emptyList(), listOf(media(11L, 1L)), 120)
        assertEquals(0, restart.removedFromFront)
    }

    @Test fun enrichmentMarkerTrackingEvictsOldEntriesAndSuppressesRecentDuplicates() {
        val tracked = LinkedHashSet<String>()
        for (id in 1..3000) {
            assertTrue(rememberBoundedIdentity(tracked, "chat:message:" + id, 768))
            assertTrue(tracked.size <= 768)
        }
        assertEquals(768, tracked.size)
        assertTrue(!rememberBoundedIdentity(tracked, "chat:message:3000", 768))
        assertTrue(rememberBoundedIdentity(tracked, "chat:message:1", 768))
        assertEquals(768, tracked.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLimitRejected() {
        TelegramMediaWindow.merge(emptyList(), emptyList(), 0)
    }
    @Test fun liveUpdatesStayWithinArm32LimitAcrossTenThousandNewMessages() {
        var retained = (1..120).map { media(11L, it.toLong()) }.reversed()
        var cursor = retained.last().messageId
        var hasMore = false
        for (newId in 121L..10120L) {
            val next = TelegramMediaWindow.prependLatest(
                retained, media(11L, newId), 120, 0, cursor, hasMore
            )
            retained = next.items
            cursor = next.nextFromMessageId
            hasMore = next.hasMore
            assertTrue(retained.size <= 120)
        }
        assertEquals(10120L, retained.first().messageId)
        assertEquals(10001L, retained.last().messageId)
        assertEquals(10001L, cursor)
        assertTrue(hasMore)
    }

    @Test fun deepPageNewArrivalDoesNotDisplaceCurrentlyVisibleOldItems() {
        val older = (1..120).map { media(11L, it.toLong()) }
        val actual = TelegramMediaWindow.prependLatest(
            older, media(11L, 99999L), 120, 180, 1L, true
        )
        assertEquals(older, actual.items)
        assertEquals(1L, actual.nextFromMessageId)
        assertTrue(!actual.visible)
    }

    @Test fun latestInsertionMovesCursorToRetainedTailWithoutSkippingOlderRows() {
        val current = (1..4).map { media(11L, it.toLong()) }.reversed()
        val updated = TelegramMediaWindow.prependLatest(
            current, media(11L, 5L), 4, 0, 1L, false
        )
        assertEquals(listOf(5L, 4L, 3L, 2L), updated.items.map { it.messageId })
        assertEquals(2L, updated.nextFromMessageId)
        assertTrue(updated.hasMore)
    }

    @Test fun existingLiveMessageIsRefreshedInPlaceWithoutChangingCursor() {
        val current = listOf(media(11L, 6L), media(11L, 5L))
        val updated = TelegramMediaWindow.prependLatest(
            current, media(11L, 5L).copy(title = "updated"), 120, 20, 1L, true
        )
        assertEquals(listOf(6L, 5L), updated.items.map { it.messageId })
        assertEquals("updated", updated.items[1].title)
        assertEquals(1L, updated.nextFromMessageId)
        assertTrue(updated.visible)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLiveUpdateWindowRejected() {
        TelegramMediaWindow.prependLatest(emptyList(), media(11L, 1L), 0, 0, 0L, false)
    }

}
