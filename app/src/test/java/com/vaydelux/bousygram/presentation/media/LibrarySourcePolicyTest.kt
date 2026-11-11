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


    @Test fun duplicateDirectoryRowsUseLatestMetadataWithoutJumpingPosition() {
        val previous = listOf(
            VideoChatItem(10L, "Original channel", false),
            VideoChatItem(20L, "Existing group", false)
        )
        val incoming = listOf(
            VideoChatItem(30L, "New channel (stale)", false),
            VideoChatItem(10L, "Original channel (stale)", false),
            VideoChatItem(30L, "New channel (updated)", false),
            VideoChatItem(20L, "Existing group (updated)", false),
            VideoChatItem(40L, "Another new channel", false)
        )
        val result = stableChatCatalog(previous, incoming)
        assertEquals(listOf(10L, 20L, 30L, 40L), result.map { it.chatId })
        assertEquals(
            listOf(
                "Original channel (stale)",
                "Existing group (updated)",
                "New channel (updated)",
                "Another new channel"
            ),
            result.map { it.title }
        )
    }

    @Test fun otherChatsKeepFirstPositionButTakeLatestDuplicateMetadata() {
        val sources = listOf(
            VideoChatItem(1L, "Saved Messages", true),
            VideoChatItem(2L, "Old title", false),
            VideoChatItem(3L, "Group", false),
            VideoChatItem(2L, "New title", false)
        )
        val result = libraryOtherChatSources(sources)
        assertEquals(listOf(2L, 3L), result.map { it.chatId })
        assertEquals(listOf("New title", "Group"), result.map { it.title })
    }

    @Test fun onlySavedMessagesLeavesEmptyAdditionalChatList() {
        assertTrue(libraryOtherChatSources(listOf(VideoChatItem(1L, "Saved Messages", true))).isEmpty())
    }
}
