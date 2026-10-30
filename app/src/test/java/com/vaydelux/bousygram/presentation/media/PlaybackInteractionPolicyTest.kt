package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.VideoChatItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackInteractionPolicyTest {
    @Test fun newChatsAppendWithoutReorderingVisibleItems() {
        val previous = listOf(
            VideoChatItem(8L, "Zeta", false),
            VideoChatItem(4L, "Alpha", false)
        )
        val updated = listOf(
            VideoChatItem(2L, "New", false),
            VideoChatItem(4L, "Alpha Updated", false),
            VideoChatItem(8L, "Zeta", false)
        )
        val stable = stableChatCatalog(previous, updated)
        assertEquals(listOf(8L, 4L, 2L), stable.map { it.chatId })
        assertEquals("Alpha Updated", stable[1].title)
    }

    @Test fun removedChatsDisappearAndDuplicatesDoNotMultiply() {
        val previous = listOf(
            VideoChatItem(1L, "Old", false),
            VideoChatItem(2L, "Keep", false)
        )
        val incoming = listOf(
            VideoChatItem(2L, "Keep", false),
            VideoChatItem(3L, "New", false),
            VideoChatItem(3L, "New", false)
        )
        assertEquals(listOf(2L, 3L), stableChatCatalog(previous, incoming).map { it.chatId })
    }

    @Test fun relativeSeekWorksWithoutDurationAndStopsAtKnownEnd() {
        assertEquals(10_000L, relativePlayerSeekTarget(0L, 10_000L, 0L))
        assertEquals(20_000L, relativePlayerSeekTarget(10_000L, 10_000L, 0L))
        assertEquals(0L, relativePlayerSeekTarget(5_000L, -10_000L, 0L))
        assertEquals(12_000L, relativePlayerSeekTarget(10_000L, 10_000L, 12_000L))
        assertEquals(Long.MAX_VALUE, relativePlayerSeekTarget(Long.MAX_VALUE, 10_000L, 0L))
    }
}
