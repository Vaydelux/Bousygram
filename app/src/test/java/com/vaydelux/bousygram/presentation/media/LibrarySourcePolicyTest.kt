package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.VideoChatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySourcePolicyTest {
    @Test fun savedMessagesIsNotRepeatedAndOtherChatsKeepTheirOrder() {
        val chats = listOf(
            VideoChatItem(1L, "Saved Messages", true),
            VideoChatItem(2L, "Movies", false),
            VideoChatItem(1L, "Saved Messages", true),
            VideoChatItem(2L, "Movies", false),
            VideoChatItem(3L, "Shows", false)
        )
        assertEquals(listOf(2L, 3L), libraryOtherChatSources(chats).map { it.chatId })
    }
    @Test fun partialAsyncDirectoryKeepsExistingChannelsUntilTerminalSnapshot() {
        val existing = listOf(
            VideoChatItem(1L, "Saved Messages", true),
            VideoChatItem(2L, "Channel", false),
            VideoChatItem(3L, "Group", false)
        )
        val partial = listOf(VideoChatItem(1L, "Saved Messages", true))
        assertEquals(
            listOf(1L, 2L, 3L),
            stableChatCatalog(existing, partial, retainMissing = true).map { it.chatId }
        )
        assertEquals(
            listOf(1L),
            stableChatCatalog(existing, partial, retainMissing = false).map { it.chatId }
        )
        val next = partial + VideoChatItem(4L, "New channel", false)
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            stableChatCatalog(existing, next, retainMissing = true).map { it.chatId }
        )
    }

    @Test fun onlySavedMessagesLeavesEmptyAdditionalChatList() {
        assertTrue(libraryOtherChatSources(listOf(VideoChatItem(1L, "Saved Messages", true))).isEmpty())
    }
}
