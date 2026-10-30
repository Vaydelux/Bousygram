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
    @Test fun onlySavedMessagesLeavesEmptyAdditionalChatList() {
        assertTrue(libraryOtherChatSources(listOf(VideoChatItem(1L, "Saved Messages", true))).isEmpty())
    }
}
